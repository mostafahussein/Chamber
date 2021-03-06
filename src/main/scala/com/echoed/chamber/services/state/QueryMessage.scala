package com.echoed.chamber.services.state

import com.echoed.chamber.services.{MessageResponse => MR, Correlated, EchoedException, Message}
import com.echoed.chamber.domain.{Topic, EchoedUser, StoryState, Image}
import com.echoed.chamber.services.adminuser.AdminUserClientCredentials
import com.echoed.chamber.services.partneruser.{PartnerUserClientCredentials => PUCC}
import com.echoed.chamber.services.adminuser.{AdminUserClientCredentials => AUCC}
import com.echoed.chamber.domain.partner.{PartnerUser, Partner}
import akka.actor.ActorRef
import com.echoed.chamber.services.partner.PartnerClientCredentials
import com.echoed.chamber.services.echoeduser.{EchoedUserClientCredentials, Follower}

sealed trait QueryMessage extends Message
sealed case class QueryException(message: String = "", cause: Throwable = null)
        extends EchoedException(message, cause)

import com.echoed.chamber.services.state.{QueryMessage => QM}
import com.echoed.chamber.services.state.{QueryException => QE}


case class FindAllTopics(page: Int = 0, pageSize: Int = 30) extends QM
case class FindAllTopicsResponse(
        message: FindAllTopics,
        value: Either[QE, List[Topic]])
        extends QM with MR[List[Topic], FindAllTopics, QE]

case class FindAllStories(page: Int = 0, pageSize: Int = 30) extends QM
case class FindAllStoriesResponse(
        message: FindAllStories,
        value: Either[QE, List[StoryState]])
        extends QM with MR[List[StoryState], FindAllStories, QE]

case class FindAllUserStories(echoedUserId: String) extends QM
case class FindAllUserStoriesResponse(
        message: FindAllUserStories,
        value: Either[QE, List[StoryState]])
        extends QM with MR[List[StoryState], FindAllUserStories, QE]

case class FindAllPartnerStories(partnerId: String) extends QM
case class FindAllPartnerStoriesResponse(
        message: FindAllPartnerStories,
        value: Either[QE, List[StoryState]])
        extends QM with MR[List[StoryState], FindAllPartnerStories, QE]

case class QueryStoriesForAdmin(
        aucc: AdminUserClientCredentials,
        page: Int = 0,
        pageSize: Int = 30,
        moderated: Option[Boolean] = None) extends QM
case class QueryStoriesForAdminResponse(
        message: QueryStoriesForAdmin,
        value: Either[QE, List[StoryState]])
        extends QM with MR[List[StoryState], QueryStoriesForAdmin, QE]

case class QueryEchoedUsersForAdmin(
        aucc: AdminUserClientCredentials,
        page: Int = 0,
        pageSize: Int = 30) extends QM
case class QueryEchoedUsersForAdminResponse(
        message: QueryEchoedUsersForAdmin,
        value: Either[QE, List[EchoedUser]])
        extends QM with MR[List[EchoedUser], QueryEchoedUsersForAdmin, QE]

case class QueryEchoedUsersByFacebookId(
        eucc: EchoedUserClientCredentials,
        facebookIds: List[String]) extends QM
case class QueryEchoedUsersByFacebookIdResponse(
        message: QueryEchoedUsersByFacebookId,
        value: Either[QE, List[String]])
        extends QM with MR[List[String], QueryEchoedUsersByFacebookId, QE]

case class QueryEchoedUsersByTwitterId(
        eucc: EchoedUserClientCredentials,
        facebookIds: List[String]) extends QM
case class QueryEchoedUsersByTwitterIdResponse(
        message: QueryEchoedUsersByTwitterId,
        value: Either[QE, List[String]])
        extends QM with MR[List[String], QueryEchoedUsersByTwitterId, QE]


case class QueryStoriesForPartner(
        pucc: PUCC,
        page: Int = 0,
        pageSize: Int = 30,
        moderated: Option[Boolean] = None) extends QM
case class QueryStoriesForPartnerResponse(
        message: QueryStoriesForPartner,
        value: Either[QE, List[StoryState]])
        extends QM with MR[List[StoryState], QueryStoriesForPartner, QE]


case class QueryPartners(aucc: AUCC, page: Int = 0, pageSize: Int = 30) extends QM
case class QueryPartnersResponse(message: QueryPartners, value: Either[QE, List[Partner]])
        extends QM with MR[List[Partner], QueryPartners, QE]

case class QueryPartnerUsers(aucc: AUCC, partnerId: String, page: Int = 0, pageSize: Int = 30) extends QM
case class QueryPartnerUsersResponse(message: QueryPartnerUsers, value: Either[QE, List[PartnerUser]])
        extends QM with MR[List[PartnerUser], QueryPartnerUsers, QE]


case class PartnerAndPartnerUsers(partner: Partner, partnerUser: PartnerUser)
case class QueryPartnersAndPartnerUsers(aucc: AUCC, page: Int = 0, pageSize: Int = 30) extends QM
case class QueryPartnersAndPartnerUsersResponse(
        message: QueryPartnersAndPartnerUsers,
        value: Either[QE, List[PartnerAndPartnerUsers]])
        extends QM with MR[List[PartnerAndPartnerUsers], QueryPartnersAndPartnerUsers, QE]


case class QueryUnique(ref: Any, correlation: Message, override val correlationSender: Option[ActorRef])
        extends QM with Correlated[Message]
case class QueryUniqueResponse(message: QueryUnique, value: Either[EchoedException, Boolean])
        extends QM with MR[Boolean, QueryUnique, EchoedException]


case class QueryFollowersForPartner(pcc: PartnerClientCredentials) extends QM
case class QueryFollowersForPartnerResponse(
        message: QueryFollowersForPartner,
        value: Either[QE, List[Follower]]) extends QM with MR[List[Follower], QueryFollowersForPartner, QE]

case class QueryPartnerIds() extends QM
case class QueryPartnerIdsResponse(
        message: QueryPartnerIds,
        value: Either[QE, List[String]]) extends QM with MR[List[String], QueryPartnerIds, QE]

case class QueryPartnerByIdOrHandle(partnerIdOrHandle: String) extends QM
case class QueryPartnerByIdOrHandleResponse(
        message: QueryPartnerByIdOrHandle,
        value: Either[QE, Partner]) extends QM with MR[Partner, QueryPartnerByIdOrHandle, QE]

case class LookupImage(imageId: String) extends QM
case class LookupImageResponse(
        message: LookupImage,
        value: Either[QE, Image]) extends QM with MR[Image, LookupImage, QE]