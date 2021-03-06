define(
    'components/utils',
    [
        'jquery',
        'cloudinary'
    ],
    function($){
        return {
            imageUrl: function(imageId, cloudName) {
                return $.cloudinary.url(imageId, { cloud_name: cloudName, cdn_subdomain: true});
            },
            scaleByWidth: function(image, width) {
                var wh = {
                    height: parseInt(image.originalHeight / image.originalWidth * width),
                    width: width
                };
                if (image.isCloudinary) return $.cloudinary.image(image.id, {
                    format: "jpg",
                    crop: "scale",
                    width: wh.width,
                    height: wh.height,
                    cloud_name: image.cloudName,
                    cdn_subdomain: true}).attr('width', wh.width).attr('height', wh.height);
                else return $('<img />').attr('src', image.storyUrl).attr('width', wh.width).attr('height', wh.height);
            },
            scaleByHeight: function(image, height) {
                var wh = {
                    height: height, //image.originalHeight / image.originalWidth * width,
                    width: parseInt(image.originalWidth / image.originalHeight * height)
                };
                if (image.isCloudinary) return $.cloudinary.image(image.id, {
                    format: "jpg",
                    crop: "scale",
                    width: wh.width,
                    height: wh.height,
                    cloud_name: image.cloudName,
                    cdn_subdomain: true}).attr('width', wh.width).attr('height', wh.height);
                else return $('<img />').attr('src', image.storyUrl).attr('width', wh.width).attr('height', wh.height);
            },
            fit: function(image, width, height) {
                var hwRatio = image.originalHeight / image.originalWidth;
                var maxHWRatio = height / width;
                if(hwRatio >= maxHWRatio){
                    var wh = {
                        height: height,
                        width: parseInt(image.originalWidth / (image.originalHeight / height))
                    };
                } else {
                    var wh = {
                        height: parseInt(image.originalHeight / (image.originalWidth / width)),
                        width: width
                    };
                }
                if (image.isCloudinary) return $.cloudinary.image(image.id, {
                    format: "jpg",
                    crop: "fit",
                    width: wh.width,
                    height: wh.height,
                    cloud_name: image.cloudName,
                    cdn_subdomain: true}).attr('width', wh.width).attr('height', wh.height);
                else return $('<img />').attr('src', image.storyUrl).attr('width', wh.width).attr('height', wh.height);
            },
            fill: function(image, width, height) {
                var hwRatio = image.originalHeight / image.originalWidth;
                var minHWRatio = height / width;
                if (minHWRatio < hwRatio) {
                    var wh = {
                        height: parseInt(image.originalHeight / (image.originalWidth / width)),
                        width: width
                    }
                } else {
                    var wh = {
                        height: height,
                        width: parseInt(image.originalWidth / (image.originalHeight / height))
                    }
                }
                if (image.isCloudinary) return $.cloudinary.image(image.id, {
                    format: "jpg",
                    crop: "fill",
                    width: wh.width,
                    height: wh.height,
                    cloud_name: image.cloudName,
                    cdn_subdomain: true}).attr('width', wh.width).attr('height', wh.height);
                else return $('<img />').attr('src', image.storyUrl).attr('width', wh.width).attr('height', wh.height);
            },
            getProfilePhotoUrl: function(echoedUser, urls) {
                var defaultImage = urls.images + "/profile_default.jpg";
                if (!echoedUser) return defaultImage;
                 
                if (echoedUser.facebookId) {
                    return "http://graph.facebook.com/" + echoedUser.facebookId + "/picture";
                } else if (echoedUser.twitterId) {
                    return "http://api.twitter.com/1/users/profile_image/" + echoedUser.twitterId;
                } else {
                    return defaultImage;
                }
            },
            arraySize: function(array){
                var size = 0, key;
                for(key in array){
                    if(array.hasOwnProperty(key)) size++;
                }
                return size;
            },
            getLoginRedirectUrl: function(url){
                var location = url ? url : encodeURIComponent(window.location.hash);
                return "login?redirect=" + location;
            },
            getSignUpRedirectUrl: function(url){
                var location = url ? url : encodeURIComponent(window.location.hash);
                return "login/register?redirect=" + location;
            },
            getFacebookLoginUrl: function(hash){
                return Echoed.facebookLogin.head +
                    encodeURIComponent(Echoed.facebookLogin.redirect + encodeURIComponent(hash)) +
                    Echoed.facebookLogin.tail;
            },
            replaceUrlsWithLink: function(text){
                var replacePattern1 = /(\b(https?|ftp):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/gim;
                text = text.replace(replacePattern1, '(<a href="$1" class="red-link" target="_blank">Link</a>)');

                //URLs starting with www. (without // before it, or it'd re-link the ones done above)
                var replacePattern2 = /\b(^|[^\/])(www\.[\S]+(\b|$))/gim;
                return text = text.replace(replacePattern2, '(<a class="red-link" href="http://$2" target="_blank">Link</a>)');
            },
            getTwitterLoginUrl: function(hash){
                return Echoed.twitterUrl + encodeURIComponent(hash);
            },
            isUrl: function(s){
                var regexp =/(http:\/\/|https:\/\/|www)(\w+:{0,1}\w*@)?(\S+)(:[0-9]+)?(\/|\/([\w#!:.?+=&%@!\-\/]))?/;
                return regexp.test(s);
            },
            escapeHtml: function(string){
                return string.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
            },
            makeUrl: function(s){
                if(s.indexOf("http") === -1){
                    s = "http://" + s;
                }
                return s;
            },
            AjaxFactory : function(params){
                var defaultParams = {
                    type: "GET",
                    xhrFields: {
                        withCredentials: true
                    },
                    dataType: 'json',
                    cache: false,
                    success: function(data){
                    }
                };
                for(var index in params){
                    defaultParams[index] = params[index];
                }
                return function(){
                    $.ajax(defaultParams);
                };
            },
            timeStampStringToDate: function(timestampString){
                var year = timestampString.substr(0,4);
                var month = timestampString.substr(4,2);
                var day = timestampString.substr(6,2);
                var hour = timestampString.substr(8,2);
                var minute = timestampString.substr(10,2);
                var second = timestampString.substr(12,2);
                var date = new Date(Date.UTC(year, month - 1, day, hour, minute, second, 0));
                return date;
            },
            timeElapsedString: function(date){
                var responseString = "";
                var todayDate = new Date();
                var dateDiff = todayDate - date;
                var dayDiff = Math.floor((dateDiff)/(1000*60*60*24));
                var hourDiff = Math.floor((dateDiff)/(1000*60*60));
                var minDiff = Math.floor((dateDiff)/(1000*60));
                if(dayDiff >= 1 ){
                    responseString = dayDiff + " day" + this.pluralize(dayDiff);
                } else if (hourDiff >= 1) {
                    responseString = hourDiff + " hour" + this.pluralize(hourDiff);
                } else {
                    responseString = minDiff + " min" + this.pluralize(minDiff);
                }
                responseString = responseString + " ago";
                return responseString;
            },
            pluralize: function(integer){
                if(integer >= 2){
                    return "s";
                } else {
                    return "";
                }
            }
        }
    }
);
