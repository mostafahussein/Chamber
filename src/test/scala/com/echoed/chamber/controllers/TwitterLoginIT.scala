package com.echoed.chamber.controllers

import com.echoed.chamber.dao.EchoedUserDao
import com.echoed.chamber.dao.TwitterUserDao
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{GivenWhenThen, FeatureSpec}
import org.scalatest.matchers.ShouldMatchers
import org.springframework.beans.factory.annotation.Autowired
import reflect.BeanProperty
import org.springframework.test.context.{TestContextManager, ContextConfiguration}
import com.echoed.util.IntegrationTest
import java.util.Properties
import org.openqa.selenium.{By, WebDriver}
import com.echoed.chamber.util.DataCreator


@RunWith(classOf[JUnitRunner])
@ContextConfiguration(locations = Array("classpath:webIT.xml"))
class TwitterLoginIT extends FeatureSpec with GivenWhenThen with ShouldMatchers {

    @Autowired @BeanProperty var echoedUserDao: EchoedUserDao = _
    @Autowired @BeanProperty var twitterUserDao: TwitterUserDao = _
    @Autowired @BeanProperty var echoHelper: EchoHelper = _
    @Autowired @BeanProperty var webDriver: WebDriver = _
    @Autowired @BeanProperty var dataCreator: DataCreator = _
    @Autowired @BeanProperty var urls: Properties = _

    new TestContextManager(this.getClass()).prepareTestInstance(this)


    var echoUrl: String = null
    var loginViewUrl: String = null
    var confirmViewUrl: String = null

    {
        echoUrl = urls.getProperty("echoUrl")
        loginViewUrl = urls.getProperty("loginViewUrl")
        confirmViewUrl = urls.getProperty("confirmViewUrl")
        echoUrl != null && loginViewUrl != null && confirmViewUrl != null
    } ensuring (_ == true, "Missing parameters")



    feature("A user can echo and create their Echoed account by login via Twitter") {

        info("As a recent purchaser")
        info("I want to be able to click on the Twitter login button")
        info("So that I can echo and create my Echoed account using my Twitter credentials")

        scenario("unknown user clicks on Twitter login button with a valid echoPossibility and is redirected to confirm page post login", IntegrationTest) {

            val twitterUser = dataCreator.twitterUser
            echoedUserDao.deleteByScreenName(twitterUser.screenName)
            twitterUserDao.deleteByScreenName(twitterUser.screenName)

            given("a request to login and echo using Twitter credentials")
            when("the user is unrecognized (no cookie) and with a valid echoPossibility")

            val (e, count) = echoHelper.setupEchoPossibility(step = "login") //this must match proper step...
            webDriver.manage().deleteCookieNamed("echoedUserId")
            webDriver.navigate.to(echoUrl + e.generateUrlParameters)
            webDriver.getCurrentUrl should startWith (echoUrl)

            //NOTE: we are assuming the user already has a valid Facebook session and has already approved Echoed...
            webDriver.findElement(By.id("twitterLogin")).click()
            webDriver.findElement(By.id("username_or_email")).sendKeys(twitterUser.screenName)
            val pass = webDriver.findElement(By.id("password"))
            pass.sendKeys(dataCreator.twitterPassword)
            pass.submit()


            then("redirect to the echo confirm page")
            webDriver.getCurrentUrl.startsWith(echoUrl) should be (true)

            //webDriver.findElement(By.id("echoit")).click()

            and("create an EchoedUser account using the Facebook info")
            val dbtwitterUser = twitterUserDao.findByScreenName(twitterUser.screenName)
            dbtwitterUser should not be (null)

            val echoedUser = echoedUserDao.findByTwitterUserId(dbtwitterUser.id)
            echoedUser should not be (null)
            val echoPossibility = e.copy(echoedUserId = echoedUser.id, step = "confirm")

            and("record the EchoPossibility in the database")
            echoHelper.validateEchoPossibility(echoPossibility, count)
        }

    }
}
