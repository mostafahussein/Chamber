package com.echoed.chamber.services.facebook

import com.echoed.chamber.services.{EchoedException, ResponseMessage => RM, Message}
import com.echoed.chamber.domain.views.FacebookPostData
import com.echoed.chamber.domain._


sealed trait FacebookMessage extends Message

sealed case class FacebookException(message: String = "", cause: Throwable = null) extends EchoedException(message, cause)


import com.echoed.chamber.services.facebook.{FacebookMessage => FM}
import com.echoed.chamber.services.facebook.{FacebookException => FE}

case class GetPostData(facebookPostData: FacebookPostData) extends FM
case class GetPostDataResponse(message: GetPostData, value: Either[FE, FacebookPostData])
        extends FM
        with RM[FacebookPostData, GetPostData, FE]

case class GetPostDataFalse(m: String = "Facebook returned false for post", facebookPost: FacebookPost) extends FE(m)

case class GetFriends(accessToken: String, facebookId: String, facebookUserId: String) extends FM
case class GetFriendsResponse(message: GetFriends, value: Either[FE, List[FacebookFriend]])
        extends FM
        with RM[List[FacebookFriend], GetFriends, FE]


case class Logout(facebookUserId: String) extends FM
case class LogoutResponse(message: Logout, value: Either[FE, Boolean])
    extends FM with RM[Boolean, Logout, FE]



