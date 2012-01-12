package com.echoed.util

import java.util.Date
import org.openqa.selenium.{WebDriver, Cookie}
import com.echoed.chamber.domain.EchoedUser
import org.scalatest.matchers.ShouldMatchers
import java.util.concurrent.TimeUnit

object WebDriverUtils extends ShouldMatchers {

    val echoedUrl = "http://www.echoed.com"
    val closetUrl = "http://v1-api.echoed.com/closet"
    val twitterUrl = "http://www.twitter.com"
    val facebookUrl = "http://www.facebook.com"

    def navigateToCloset(webDriver: WebDriver, echoedUser: EchoedUser) = {
        val cookie = new Cookie.Builder("echoedUserId", echoedUser.id)
                .domain(".echoed.com")
                .path("/")
                .expiresOn(new Date((new Date().getTime + (1000*60*60*24))))
                .build()

        webDriver.get(echoedUrl)
        webDriver.manage().deleteAllCookies()
        webDriver.manage().addCookie(cookie)
        webDriver.get(closetUrl)

        webDriver.getTitle should be ("My Exhibit")

        val pageSource = webDriver.getPageSource
        pageSource should include(echoedUser.name)
        pageSource
    }

    def clearEchoedCookies(webDriver: WebDriver) {
        webDriver.get(echoedUrl)
        webDriver.manage.deleteAllCookies()
    }

    def clearTwitterCookies(webDriver: WebDriver) {
        webDriver.get(twitterUrl)
        webDriver.manage.deleteAllCookies()
    }

    def clearFacebookCookies(webDriver: WebDriver) {
        webDriver.get(facebookUrl)
        webDriver.manage.deleteAllCookies()
    }

}
