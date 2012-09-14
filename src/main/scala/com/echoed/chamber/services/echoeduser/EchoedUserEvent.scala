package com.echoed.chamber.services.echoeduser

import com.echoed.chamber.services.{UpdatedEvent, CreatedEvent, Event}
import com.echoed.chamber.domain._


sealed trait EchoedUserEvent extends Event
sealed trait StoryEvent extends Event {
    def story: StoryState
}

import com.echoed.chamber.services.echoeduser.{EchoedUserEvent => EUE}
import com.echoed.chamber.services.echoeduser.{StoryEvent => SE}

case class StoryUpdated(story: StoryState) extends SE with UpdatedEvent
case class StoryCreated(story: StoryState) extends SE with CreatedEvent
case class StoryTagged(story: StoryState, originalTag: String, newTag: String) extends SE with UpdatedEvent
case class ChapterCreated(story: StoryState, chapter: Chapter, chapterImages: List[ChapterImage]) extends SE with CreatedEvent
case class ChapterUpdated(story: StoryState, chapter: Chapter, chapterImages: List[ChapterImage]) extends SE with UpdatedEvent
case class CommentCreated(story: StoryState, comment: Comment) extends SE with UpdatedEvent


private[services] case class EchoedUserCreated(
                echoedUser: EchoedUser,
                echoedUserSettings: EchoedUserSettings,
                facebookUser: Option[FacebookUser] = None,
                twitterUser: Option[TwitterUser] = None) extends EUE with CreatedEvent

private[services] case class EchoedUserUpdated(
                echoedUser: EchoedUser,
                echoedUserSettings: EchoedUserSettings,
                facebookUser: Option[FacebookUser],
                twitterUser: Option[TwitterUser]) extends EUE with UpdatedEvent

private[services] case class NotificationCreated(notification: Notification) extends EUE with CreatedEvent

private[services] case class NotificationUpdated(notification: Notification) extends EUE with UpdatedEvent

