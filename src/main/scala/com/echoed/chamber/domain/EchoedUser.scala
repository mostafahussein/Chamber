package com.echoed.chamber.domain

import java.util.{UUID, Date}


case class EchoedUser(
        id: String,
        updatedOn: Date,
        createdOn: Date,
        name: String,
        email: String,
        screenName: String,
        facebookUserId: String,
        facebookId: String,
        twitterUserId: String,
        twitterId: String) {

    def this(
            name: String,
            email: String,
            screenName: String,
            facebookUserId: String,
            facebookId: String,
            twitterUserId: String,
            twitterId: String) = this(
        UUID.randomUUID.toString,
        new Date,
        new Date,
        name,
        email,
        screenName,
        facebookUserId,
        facebookId,
        twitterUserId,
        twitterId)

    def this(
            id:String,
            name:String,
            email:String,
            screenName: String,
            facebookUserId: String,
            facebookId:String,
            twitterUserId: String,
            twitterId: String) = this(
        id,
        null,
        null,
        name,
        email,
        screenName,
        facebookUserId,
        facebookId,
        twitterUserId,
        twitterId)

    def this(id: String, name: String, email: String) = this(
        id,
        null,
        null,
        name,
        email,
        null,
        null,
        null,
        null,
        null)

    def this(facebookUser: FacebookUser) = this(
        UUID.randomUUID.toString,
        new Date,
        new Date,
        facebookUser.name,
        facebookUser.email,
        null,
        facebookUser.id,
        facebookUser.facebookId,
        null,
        null)

    def this(twitterUser: TwitterUser) = this(
        UUID.randomUUID.toString,
        new Date,
        new Date,
        twitterUser.name,
        null,
        twitterUser.screenName,
        null,
        null,
        twitterUser.id,
        twitterUser.twitterId)

    def assignFacebookUser(fu: FacebookUser) =
        this.copy(facebookId = fu.facebookId, facebookUserId = fu.id, email = Option(email).getOrElse(fu.email))

    def assignTwitterUser(tu: TwitterUser) =
        this.copy(twitterId = tu.twitterId, twitterUserId = tu.id, screenName = tu.screenName)
}
