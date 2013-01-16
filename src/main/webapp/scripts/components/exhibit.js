define(
    [
        'jquery',
        'backbone',
        'underscore',
        'models/story',
        'components/storyBrief',
        'components/utils',
        'isotope'
    ],
    function($, Backbone, _, ModelStory, StoryBrief, utils, isotope ){
        return Backbone.View.extend({
            el: '#content',
            initialize: function(options){
                _.bindAll(this);
                this.EvAg = options.EvAg;
                this.properties = options.properties;
                this.modelUser = options.modelUser;
                this.EvAg.bind('exhibit/init', this.init);
                this.EvAg.bind('infiniteScroll', this.next);
                this.EvAg.bind('exhibit/story/next', this.nextStory);
                this.EvAg.bind('exhibit/story/previous', this.previousStory);
                this.element = $(this.el);
                this.exhibit = $('#exhibit');
            },
            init: function(options){
                var self = this;
                var data = options.data;
                self.jsonUrl = options.jsonUrl;
                self.personal = false;
                self.personal = options.personal;
                self.EvAg.trigger('infiniteScroll/on');
                self.nextPage = data.nextPage ? data.nextPage : null;
                self.stories = {
                    array: [],
                    hash: {}
                };
                if (self.isotopeOn === true) {
                    self.exhibit.isotope("destroy")
                }
                self.exhibit.empty();
                self.exhibit.isotope({
                    itemSelector: '.item_wrap'
                });
                self.isotopeOn = true;
                self.render(data);
            },
            nextStory: function(storyId){
                var self = this;
                var index = self.stories.hash[storyId];
                if((index + 1) >= self.stories.array.length){
                    self.next();
                }
                if((index + 1) < self.stories.array.length){
                    window.location.hash = "#story/" + self.stories.array[index + 1];
                }
            },
            previousStory: function(storyId){
                var self = this;
                var index = self.stories.hash[storyId];
                if(index> 0){
                    window.location.hash = "#story/" + self.stories.array[index - 1];
                }
            },
            render: function(data){
                var self = this;
                if(self.addStories(data) || self.addFriends(data) || self.addCommunities(data)){
                    self.EvAg.trigger('infiniteScroll/unlock');
                }
            },
            next: function(){
                var self = this;
                if(self.nextPage !== null){
                    self.EvAg.trigger('infiniteScroll/lock');
                    var url = self.jsonUrl + "?page=" + (self.nextPage);
                    self.nextPage = null;
                    utils.AjaxFactory({
                        url: url,
                        success: function(data){
                            if(data.nextPage !== null) {
                                self.nextPage = data.nextPage;
                            }
                            self.render(data);
                        }
                    })();
                }
            },
            addCommunities: function(data){
                var self = this;
                var communityFragment = $('<div></div>');
                var communityAdded = false;
                if(data.communities){
                    $.each(data.communities, function(index, community){
                        var communityDiv = $('<div></div>').addClass("item_wrap");
                        $('<a class="item_content community"></a>').text(community.id).appendTo(communityDiv).attr("href", "#community/" + community.id);
                        communityFragment.append(communityDiv);
                        communityAdded = true;
                    });
                    self.exhibit.isotope('insert', communityFragment.children());
                }
                return communityAdded;
            },
            addFriends: function(data){
                var self = this;
                var friendsFragment = $('<div></div>');
                var friendsAdded = false;
                if(data && data.length > 0){
                    $.each(data, function(index, friend){
                        var friendImage = $('<div class="friend-img"></div>');
                        var friendText = $('<div class="friend-text"></div>').text(friend.name);
                        var  a = $('<a></a>').attr("href","#user/" + friend.echoedUserId).addClass('item_wrap');
                        $('<img />').attr("height","50px").attr("src",utils.getProfilePhotoUrl(friend, self.properties.urls)).appendTo(friendImage);
                        $('<div class="item_content friend"></div>').append(friendImage).append(friendText).appendTo(a).addClass('clearfix');
                        friendsFragment.append(a);
                        friendsAdded = true;
                    });
                    self.exhibit.isotope('insert', friendsFragment.children());
                }
            },
            addStories: function(data){
                var self = this;
                var storiesFragment = $('<div></div>');
                var storiesAdded = false;
                if(data.stories){
                    $.each(data.stories, function(index, story){
                        if(story.chapters.length > 0 || self.personal == true){
                            self.stories.hash[story.id] = self.stories.array.length;
                            self.stories.array.push(story.id);
                            var storyDiv = $('<div></div>').addClass('item_wrap');
                            var modelStory = new ModelStory(story, { properties: self.properties});
                            var storyComponent = new StoryBrief({el : storyDiv, data: story, EvAg: self.EvAg, Personal: self.personal, properties: self.properties, modelUser: self.modelUser, modelStory: modelStory});
                            if(story.story.image !== null){
                                if(story.story.image.originalUrl !== null){
                                    storiesFragment.append(storyDiv)
                                } else {
                                    storyDiv.imagesLoaded(function(){
                                        self.exhibit.isotope('insert', storyDiv);
                                    });
                                }
                            } else {
                                storiesFragment.append(storyDiv);
                            }

                        }
                        storiesAdded = true;
                    });
                    self.exhibit.isotope('insert', storiesFragment.children(), function(){
                        self.EvAg.trigger('infiniteScroll/unlock');
                    });
                }
                return storiesAdded;
            }
        });
    }
);
