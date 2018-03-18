/*
 * Copyright 2018 Chris Kelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.tolstoy.censorship.twitter.checker.app.analyzer;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;
import java.time.Instant;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.tolstoy.basic.api.tweet.*;
import com.tolstoy.basic.api.utils.IResourceBundleWithFormatting;
import com.tolstoy.basic.app.utils.Utils;
import com.tolstoy.censorship.twitter.checker.api.preferences.IPreferences;
import com.tolstoy.censorship.twitter.checker.api.analyzer.IAnalysisReportRepliesBasic;
import com.tolstoy.censorship.twitter.checker.api.analyzer.IAnalysisReportRepliesItemBasic;
import com.tolstoy.censorship.twitter.checker.api.analyzer.AnalysisReportItemBasicTweetStatus;
import com.tolstoy.censorship.twitter.checker.api.searchrun.ISearchRunReplies;
import com.tolstoy.censorship.twitter.checker.api.snapshot.ISnapshotUserPageIndividualTweet;

class AnalysisReportRepliesBasic implements IAnalysisReportRepliesBasic {
	private static final Logger logger = LogManager.getLogger( AnalysisReportRepliesBasic.class );

	private static final String LOW_QUALITY_TEST = "low";
	private static final int BOOST_REPLIES = 4;
	private static final int BOOST_RETWEETS = 2;
	private static final int BOOST_FAVORITES = 1;

	private final ISearchRunReplies searchRun;
	private final ITweetFactory tweetFactory;
	private final IPreferences prefs;
	private final IResourceBundleWithFormatting bundle;
	private final List<IAnalysisReportRepliesItemBasic> reportItems;
	private Map<String,String> attributes;

	AnalysisReportRepliesBasic( ISearchRunReplies searchRun, ITweetFactory tweetFactory, IPreferences prefs, IResourceBundleWithFormatting bundle ) {
		this.tweetFactory = tweetFactory;
		this.searchRun = searchRun;
		this.prefs = prefs;
		this.bundle = bundle;
		this.attributes = new HashMap<String,String>();
		this.reportItems = new ArrayList<IAnalysisReportRepliesItemBasic>();
	}

	protected IAnalysisReportRepliesItemBasic createReportItem( ITweet originalTweet, ISnapshotUserPageIndividualTweet replyPage ) {
		AnalysisReportRepliesItemBasic ret = new AnalysisReportRepliesItemBasic( tweetFactory, originalTweet, replyPage );

		ret.setAttribute( "total replies", "" + replyPage.getNumReplies() );

		List<ITweet> tweets = replyPage.getTweetCollection().getTweets();
		ret.setAttribute( "originaltweets", summarizeTweetList( tweets ) );

		int numNewerTweets = countNewerTweets( originalTweet, tweets );
		int percentNewerTweets = (int) Math.floor( ( 100.0 * (float) numNewerTweets ) / (float) replyPage.getNumReplies() );
		int percentComplete = (int) Math.floor( ( 100.0 * (float) tweets.size() ) / (float) replyPage.getNumReplies() );

		ret.setAttribute( "numNewerTweets", "" + numNewerTweets );
		ret.setAttribute( "percentNewerTweets", "" + percentNewerTweets );
		ret.setAttribute( "percentComplete", "" + percentComplete );

		ITweet foundOriginalTweet = replyPage.getTweetCollection().getTweetByID( originalTweet.getID() );

		ret.setAttribute( "foundOriginalTweet", ( foundOriginalTweet != null ? foundOriginalTweet.getSummary() : " IS NULL" ) );

			//	the reply isn't in the page. It might have been censored, or it just might be further
			//	down the page if we don't have all the tweets from the page. Set the status accordingly.
		if ( foundOriginalTweet == null ) {
			ret.setTweetStatus( getTweetNotFoundStatus( originalTweet, percentNewerTweets, percentComplete, replyPage ) );
			ret.setAttribute( "TWEETNOTFOUND, status set to ", "" + ret.getTweetStatus() );
			return ret;
		}

		int pageOrder = replyPage.getTweetCollection().getTweetOrderByID( originalTweet.getID() );
		int interactionOrder = getTweetInteractionOrder( ret, tweets, originalTweet.getID() );
		int dateOrder = getTweetDateOrder( ret, tweets, originalTweet.getID() );

		int percentComparedToInteractionOrder = (int) Math.floor( ( 100.0 * (float) ( interactionOrder - pageOrder ) ) / (float) replyPage.getNumReplies() );
		int percentComparedToDateOrder = (int) Math.floor( ( 100.0 * (float) ( dateOrder - pageOrder ) ) / (float) replyPage.getNumReplies() );

		ret.setAttribute( "pageOrder", "" + pageOrder );
		ret.setAttribute( "interactionOrder", "" + interactionOrder );
		ret.setAttribute( "dateOrder", "" + dateOrder );
		ret.setAttribute( "percentComparedToInteractionOrder", "" + percentComparedToInteractionOrder );
		ret.setAttribute( "percentComparedToDateOrder", "" + percentComparedToDateOrder );

		ret.setRank( pageOrder );
		ret.setExpectedRankByInteraction( interactionOrder );
		ret.setExpectedRankByDate( dateOrder );

		String quality = Utils.trimDefault( foundOriginalTweet.getAttribute( "quality" ) ).toLowerCase();
		if ( quality.indexOf( LOW_QUALITY_TEST ) > -1 ) {
				//	the reply is hidden behind the "Show more replies" button
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.CENSORED_HIDDEN );
		}
		else if ( pageOrder <= 2 ) {
				//	first two tweets, no matter how many
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.VISIBLE_BEST );
		}
		else if ( percentComparedToInteractionOrder > 50 && percentComparedToDateOrder > 50 ) {
				//	better than 50% of similar tweets
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.VISIBLE_BETTER );
		}
		else if ( percentComparedToInteractionOrder >= 0 && percentComparedToDateOrder >= 0 ) {
				//	better than 0% to 50% of similar tweets
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.VISIBLE_NORMAL );
		}
		else if ( percentComparedToInteractionOrder >= -50 && percentComparedToDateOrder >= -50 ) {
				//	worse than 0% to 50% of similar tweets
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.VISIBLE_WORSE );
		}
		else {
				//	the basement
			ret.setTweetStatus( AnalysisReportItemBasicTweetStatus.VISIBLE_WORST );
		}

		ret.setAttribute( "setTweetStatus", "" + ret.getTweetStatus() );

		return ret;
	}

	protected AnalysisReportItemBasicTweetStatus getTweetNotFoundStatus( ITweet originalTweet, int percentNewerTweets, int percentComplete,
																			ISnapshotUserPageIndividualTweet replyPage ) {
		if ( replyPage.getComplete() ) {
				//	tweet isn't there and replyPage is complete
			return AnalysisReportItemBasicTweetStatus.CENSORED_NOTFOUND;
		}
		else if ( percentNewerTweets < 30 && percentComplete < 30 ) {
			return AnalysisReportItemBasicTweetStatus.SUPPRESSED_NORMAL;
		}
		else if ( percentNewerTweets < 70 && percentComplete < 70 ) {
			return AnalysisReportItemBasicTweetStatus.SUPPRESSED_WORSE;
		}
		else {
			return AnalysisReportItemBasicTweetStatus.SUPPRESSED_WORST;
		}
	}

	protected int getTweetOrder( List<ITweet> tweets, long tweetID ) {
		int order = 1;
		for ( ITweet tweet : tweets ) {
			if ( tweet.getID() == tweetID ) {
				return order;
			}
			order++;
		}

		return 0;
	}

	protected int getTweetDateOrder( AnalysisReportRepliesItemBasic ret, List<ITweet> tweets, long tweetID ) {
		List<ITweet> tempList = new ArrayList<ITweet>( tweets );

		Collections.sort( tempList, new TweetDateComparator( TweetComparatorDirection.ASC ) );

		ret.setAttribute( "tweetsInDateOrder", summarizeTweetList( tempList ) );

		return getTweetOrder( tempList, tweetID );
	}

	protected int getTweetInteractionOrder( AnalysisReportRepliesItemBasic ret, List<ITweet> tweets, long tweetID ) {
		List<ITweet> tempList = new ArrayList<ITweet>( tweets );

		Collections.sort( tempList, new TweetInteractionComparator( BOOST_REPLIES, BOOST_RETWEETS, BOOST_FAVORITES, TweetComparatorDirection.DESC ) );

		ret.setAttribute( "tweetsInInteractionOrder", summarizeTweetList( tempList ) );

		return getTweetOrder( tempList, tweetID );
	}

	@Override
	public void run() throws Exception {
		reportItems.clear();

		ITweetCollection tweetColTimeline = searchRun.getTimeline().getTweetCollection();

		Set<Long> originalReplyIDs = searchRun.getOriginalReplyIDs();

		for ( Long originalReplyID : originalReplyIDs ) {
			ITweet originalTweet = tweetColTimeline.getTweetByID( originalReplyID );
			ISnapshotUserPageIndividualTweet replyPage = searchRun.getOriginalReplyByID( originalReplyID );

			//logger.info( "originalTweet=" + originalTweet.getSummary() );
			//logger.info( "replyPage=" + replyPage );

			if ( originalTweet != null && replyPage != null ) {
				reportItems.add( createReportItem( originalTweet, replyPage ) );
			}
		}
	}

	protected String summarizeTweetList( List<ITweet> tweets ) {
		List<String> temp = new ArrayList<String>( tweets.size() );

		for ( ITweet tweet : tweets ) {
			temp.add( tweet.getSummary() );
		}

		return StringUtils.join( temp, "\n" );
	}

	@Override
	public String getAnalysisType() {
		return "basic";
	}

	@Override
	public String getName() {
		return bundle.getString( "arb_name", searchRun.getInitiatingUser().getHandle(), "" + searchRun.getStartTime() );
	}

	@Override
	public String getDescription() {
		return bundle.getString( "arb_description", searchRun.getInitiatingUser().getHandle(), "" + searchRun.getStartTime() );
	}

	@Override
	public ISearchRunReplies getSearchRun() {
		return searchRun;
	}

	@Override
	public Map<String,String> getAttributes() {
		return attributes;
	}

	@Override
	public List<IAnalysisReportRepliesItemBasic> getItems() {
		return reportItems;
	}

	@Override
	public String toString() {
		return new ToStringBuilder( this )
		.append( "reportItems", reportItems )
		.append( "attributes", attributes )
		.toString();
	}

	protected int countNewerTweets( ITweet testTweet, List<ITweet> tweets ) {
		int time = Utils.parseIntDefault( testTweet.getAttribute( "time" ) );
		int count = 0;

		for ( ITweet tweet : tweets ) {
			int tempTime = Utils.parseIntDefault( tweet.getAttribute( "time" ) );
			if ( tempTime > time ) {
				count++;
			}
		}

		return count;
	}
}
