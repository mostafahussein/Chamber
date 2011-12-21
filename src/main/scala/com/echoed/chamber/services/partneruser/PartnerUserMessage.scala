package com.echoed.chamber.services.partneruser

import com.echoed.chamber.services.{EchoedException, ResponseMessage => RM, Message}
import com.echoed.chamber.domain.RetailerUser


sealed trait PartnerUserMessage extends Message
sealed case class PartnerUserException(message: String = "", cause: Throwable = null) extends EchoedException(message, cause)

import com.echoed.chamber.services.partneruser.{PartnerUserMessage => PUM}
import com.echoed.chamber.services.partneruser.{PartnerUserException => PUE}


case class Login(email: String, password: String) extends PUM
case class LoginError(m: String = "", c: Throwable = null) extends PUE(m, c)
case class LoginResponse(message: Login, value: Either[LoginError, PartnerUserService])
        extends PUM with RM[PartnerUserService, Login, LoginError]

case class GetPartnerUser() extends PUM
case class GetPartnerUserResponse(message: GetPartnerUser, value: Either[PartnerUserException, RetailerUser])
        extends PUM with RM[RetailerUser, GetPartnerUser, PUE]

case class CreatePartnerUserService(email: String) extends PUM
case class CreatePartnerUserServiceResponse(
        message: CreatePartnerUserService,
        value: Either[PUE, PartnerUserService])
        extends PUM with RM[PartnerUserService, CreatePartnerUserService, PUE]

case class Locate(partnerUserId: String) extends PUM
case class LocateResponse(
        message: Locate,
        value: Either[PUE, PartnerUserService])
        extends PUM with RM[PartnerUserService, Locate, PUE]

