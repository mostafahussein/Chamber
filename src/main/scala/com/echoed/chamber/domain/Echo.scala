package com.echoed.chamber.domain

import java.util.{UUID, Date}
import java.lang.{Math => JMath}


case class Echo(
        id: String,
        updatedOn: Date,
        createdOn: Date,
        retailerId: String,
        customerId: String,
        productId: String,
        boughtOn: Date,
        orderId: String,
        price: Float,
        imageUrl: String,
        echoedUserId: String,
        facebookPostId: String,
        twitterStatusId: String,
        echoPossibilityId: String,
        landingPageUrl: String,
        retailerSettingsId: String,
        productName: String,
        category: String,
        brand: String,
        description: String,
        echoMetricsId: String,
        echoClickId: String) {

    def this(
            retailerId: String,
            customerId: String,
            productId: String,
            boughtOn: Date,
            orderId: String,
            price: Float,
            imageUrl: String,
            echoedUserId: String,
            facebookPostId: String,
            twitterStatusId: String,
            echoPossibilityId: String,
            landingPageUrl: String,
            retailerSettingsId: String,
            productName: String,
            category: String,
            brand: String,
            description: String,
            echoMetricsId: String,
            echoClickId:String) = this(
        UUID.randomUUID.toString,
        new Date,
        new Date,
        retailerId,
        customerId,
        productId,
        boughtOn,
        orderId,
        price,
        imageUrl,
        echoedUserId,
        facebookPostId,
        twitterStatusId,
        echoPossibilityId,
        landingPageUrl,
        retailerSettingsId,
        productName,
        category,
        brand,
        description,
        echoMetricsId,
        echoClickId)


    def this(id: String, boughtOn: Date, price: Int, imageUrl: String, landingPageUrl: String) = this(
        id,
        null,
        null,
        null,
        null,
        null,
        boughtOn,
        null,
        price,
        imageUrl,
        null,
        null,
        null,
        null,
        landingPageUrl,
        null,
        null,
        null,
        null,
        null,
        null,
        null)


    def this(
            echoPossibility: EchoPossibility,
            retailerSettings: RetailerSettings) = this(
        echoPossibility.retailerId,
        echoPossibility.customerId,
        echoPossibility.productId,
        echoPossibility.boughtOn,
        echoPossibility.orderId,
        echoPossibility.price,
        echoPossibility.imageUrl,
        echoPossibility.echoedUserId,
        null,
        null,
        echoPossibility.id,
        echoPossibility.landingPageUrl,
        retailerSettings.id,
        echoPossibility.productName,
        echoPossibility.category,
        echoPossibility.brand,
        echoPossibility.description,
        null,
        echoPossibility.echoClickId)

    def this(
            id: String,
            boughtOn: Date,
            price: Float,
            imageUrl: String,
            landingPageUrl: String) = this(
        id,
        null,
        null,
        null,
        null,
        null,
        boughtOn,
        null,
        price,
        imageUrl,
        null,
        null,
        null,
        null,
        landingPageUrl,
        null,
        null,
        null,
        null,
        null,
        null,
        null)

}


