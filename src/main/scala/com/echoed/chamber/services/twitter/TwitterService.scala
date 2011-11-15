package com.echoed.chamber.services.twitter

import twitter4j.auth.RequestToken
import twitter4j.auth.AccessToken
import akka.dispatch.Future
import com.echoed.util.FutureHelper
import com.echoed.chamber.domain.TwitterUser

/**
 * Created by IntelliJ IDEA.
 * User: jonlwu
 * Date: 11/7/11
 * Time: 3:58 PM
 * To change this template use File | Settings | File Templates.
 */

trait TwitterService {
  def twitterUser: Option[TwitterUser] = FutureHelper.get[TwitterUser](getTwitterUser _)


  def getRequestToken(): Future[RequestToken]
  def getAccessToken(oAuthVerifier:String): Future[AccessToken]

  def getUser(): Future[TwitterUser]
  def getTwitterUser(): Future[TwitterUser]

  def assignEchoedUserId(id:String): Future[TwitterUser]

  def updateStatus(status: String): Future[String]


}