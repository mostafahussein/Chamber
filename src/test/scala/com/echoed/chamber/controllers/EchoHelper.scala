package com.echoed.chamber.controllers

import org.springframework.beans.factory.annotation.Autowired
import reflect.BeanProperty
import org.scalatest.matchers.ShouldMatchers
import java.util.Date
import com.echoed.chamber.domain.{RetailerSettings, Retailer, EchoPossibility}
import com.echoed.chamber.dao.{RetailerSettingsDao, RetailerDao, EchoPossibilityDao}


class EchoHelper extends ShouldMatchers {

    @Autowired @BeanProperty var echoPossibilityDao: EchoPossibilityDao = _
    @Autowired @BeanProperty var retailerDao: RetailerDao = _
    @Autowired @BeanProperty var retailerSettingsDao: RetailerSettingsDao = _

    def setupEchoPossibility(
            echoPossibility: EchoPossibility,
            retailer: Retailer,
            retailerSettings: RetailerSettings) = {

        echoPossibility.retailerId should equal(retailer.id)
        retailerSettings.retailerId should equal(retailer.id)

        retailerDao.deleteByName(retailer.name)
        retailerSettingsDao.deleteByRetailerId(retailer.id)
        echoPossibilityDao.deleteByRetailerId(retailer.id)

        retailerDao.insert(retailer)
        retailerSettingsDao.insert(retailerSettings)

        val count = echoPossibilityDao.selectCount
        (echoPossibility, count)
    }

    def setupEchoPossibility(
            retailerId: String = "testRetailerId",
            customerId: String = "testRetailerCustomerId",
            productId: String = "testProductId",
            boughtOn: Date = new Date(1320871016126L), //Wed Nov 09 15:36:56 EST 2011,
            step: String = "button",
            orderId: String = "testOrderId",
            price: Int = 100, //one dollar
            imageUrl: String = "http://v1-cdn.echoed.com/Pic1.jpg",
            echoedUserId: String = null,
            echoId: String = null,
            landingPageUrl: String = "http://echoed.com",
            productName: String = "My Awesome Boots",
            category: String = "Footwear",
            brand: String = "Nike") = {

        retailerDao.deleteById(retailerId)
        retailerDao.insert(Retailer(
            retailerId,
            new Date,
            new Date,
            retailerId
        ))

        echoPossibilityDao.deleteByRetailerId(retailerId)
        val echoPossibility = new EchoPossibility(
                retailerId,
                customerId,
                productId,
                boughtOn,
                step,
                orderId,
                price,
                imageUrl,
                echoedUserId,
                echoId,
                landingPageUrl,
                productName,
                category,
                brand);

        val count = echoPossibilityDao.selectCount
        (echoPossibility, count)
    }

    def validateEchoPossibility(echoPossibility: EchoPossibility, count: Long) {
        validateCountIs(count + 1)
        val recordedEchoPossibility = echoPossibilityDao.findById(echoPossibility.id)
        recordedEchoPossibility.id should not be (null)
        recordedEchoPossibility.step should equal (echoPossibility.step)
        recordedEchoPossibility.echoedUserId should equal (echoPossibility.echoedUserId)
        recordedEchoPossibility.echoId should equal (echoPossibility.echoId)
    }

    def getEchoPossibilityCount = echoPossibilityDao.selectCount

    def validateCountIs(count: Long) {
        //This is a nasty hack to allow time for the underlying database to be updated.  To repeat this bug start
        //Chamber up in debug mode with no breakpoints set and then run this test, if your machine is like mine the test
        //will pass the first time but fail the second time (of course it should always pass so you will have to force a database update).
        //Anyway, for some reason manually flushing the SQL statement caches does not work...
        Thread.sleep(1000)
        count should equal (echoPossibilityDao.selectCount)
    }
}