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
package com.tolstoy.basic.api.tweet;

import java.io.*;
import java.net.*;
import java.util.*;
import com.tolstoy.basic.app.utils.StringList;

/**
 * Represents a tweet. Each tweet has an ID, an ITweetUser, an order (when part of a set of tweets, 0 otherwise),
 * a set of classes (the CSS classes in the HTML), a set of mentions (also from the HTML), and a set of attributes.
 * <p>
 * Values such as the tweet text are stored in the attributes; some attributes may or may not exist for some tweets.
 * Possible keys include:
 * <ul>
 * <li>youblock</li>
 * <li>followsyou</li>
 * <li>youfollow</li>
 * <li>userid</li>
 * <li>name</li>
 * <li>screenname</li>
 * <li>tweetnonce</li>
 * <li>conversationid</li>
 * <li>permalinkpath</li>
 * <li>itemid</li>
 * <li>tweetstatinitialized</li>
 * <li>disclosuretype</li>
 * <li>hascards</li>
 * <li>replytousersjson</li>
 * <li>avatarURL</li>
 * <li>fullname</li>
 * <li>verifiedText</li>
 * <li>username</li>
 * <li>time</li>
 * <li>tweettext</li>
 * <li>photourl</li>
 * <li>replycount</li>
 * <li>retweetcount</li>
 * <li>favoritecount</li>
 * <li>suggestionjson</li>
 * <li>videothumburl</li>
 * <li>hasparenttweet</li>
 * <li>isreplyto (== "true" if this is a reply)</li>
 * <li>retweetid</li>
 * <li>innertweetid</li>
 * <li>innertweetrawhref</li>
 * <li>quality</li>
 * <li>componentcontext</li>
 * </ul>
 */
public interface ITweet {
	/** Get the tweet ID
	 * @return the tweet ID
	*/
	long getID();

	/** Set the tweet ID
	 * @param id the tweet ID
	*/
	void setID( long id );

	/** Get a brief summary of this tweet.
	 * @return a brief summary
	*/
	String getSummary();

	/** Get a brief summary of this tweet as a map.
	 * @return a brief summary
	*/
	Map<String,String> getAsMapBasic();

	/** Get whether this tweet is valid or not.
	 * A tweet is considered invalid if it isn't from a user (for instance,
	 * some lists of tweets contain placeholder(s) that don't represent
	 * real tweets.
	 * @return true if valid, false otherwise
	*/
	boolean isValid();

	/** Get the user associated with this tweet.
	 * @return the user
	*/
	ITweetUser getUser();

	/** Set the user associated with this tweet.
	 * @param user the user
	*/
	void setUser( ITweetUser user );

	/** If this is a reply to another tweet, return the tweet ID of
	 * the other tweet.
	 * @return the other tweet's ID or 0 if this is not a reply
	*/
	long getRepliedToTweetID();

	/** If this is a reply to another tweet, return the handle of
	 * the other tweet's user.
	 * @return the other tweet's handle or "" if this is not a reply
	*/
	String getRepliedToHandle();

	/** If this is a reply to another tweet, return the user ID of
	 * the other tweet's user.
	 * @return the other tweet's user ID or 0 if this is not a reply
	*/
	long getRepliedToUserID();

	/** Get the supposed quality (as given in the HTML).
	 * @return the supposed quality
	*/
	TweetSupposedQuality getSupposedQuality();

	/** Get all the attributes.
	 * @return a map of attributes
	*/
	Map<String,String> getAttributes();

	/** Set all the attributes.
	 * @param attributes a map of attributes
	*/
	void setAttributes( Map<String,String> attributes );

	/** Get a single attribute.
	 * @param key the name of the attribute
	 * @return the attribute value or null
	*/
	String getAttribute( String key );

	/** Set a single attribute.
	 * @param key the name of the attribute
	 * @param value the value of the attribute
	*/
	void setAttribute( String key, String value );

	/** Get the set of classes from the .tweet element in the HTML
	 * A pinned tweet has "user-pinned"
	 * @return the classes
	*/
	StringList getClasses();

	/** Set the classes from the .tweet element in the HTML
	 * @param classes the classes
	*/
	void setClasses( StringList classes );

	/** Get the mentions from the HTML
	 * @return the mentions
	*/
	StringList getMentions();

	/** Set the mentions from the HTML
	 * @param mentions the mentions
	*/
	void setMentions( StringList mentions );
}
