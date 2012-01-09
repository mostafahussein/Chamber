package com.echoed.chamber.services.echoeduser

import akka.dispatch.Future
import com.echoed.util.FutureHelper

import com.echoed.chamber.services.facebook.FacebookService
import com.echoed.chamber.services.twitter.TwitterService
import com.echoed.chamber.domain._
import com.echoed.chamber.domain.views.Closet
import com.echoed.chamber.domain.views.Feed


trait EchoedUserService {

    def getEchoedUser: Future[GetEchoedUserResponse]

    def assignTwitterService(twitterService:TwitterService): Future[AssignTwitterServiceResponse]

    def assignFacebookService(facebookService:FacebookService): Future[AssignFacebookServiceResponse]

    def getTwitterFollowers: Future[Array[TwitterFollower]]

    def echoTo(echoTo: EchoTo): Future[EchoToResponse]

    def echoToFacebook(echo: Echo, message: Option[String]): Future[EchoToFacebookResponse]

    def echoToTwitter(echo:Echo,  message: Option[String]): Future[EchoToTwitterResponse]

    def getCloset: Future[GetExhibitResponse]

    def getFeed: Future[GetFeedResponse]

    def getFriendCloset(echoedFriendId: String): Future[GetFriendExhibitResponse]

    def getFriends: Future[GetEchoedFriendsResponse]

    def friends: Future[List[EchoedFriend]]
}

