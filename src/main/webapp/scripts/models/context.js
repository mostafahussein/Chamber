define(
    [
        'backbone'
    ],
    function(Backbone){
        return Backbone.Model.extend({
            baseUrl: function(){
                if(this.has("contextType") && this.has("id")){
                    if (this.get("contextType") === "me" && this.id !== "feed"){
                        return this.get("contextType").toLowerCase() + "/"
                    } else {
                        return this.get("contextType").toLowerCase() + "/" + this.id + "/";
                    }
                } else {
                    return "explore/";
                }
            },
            getContentType: function(){
                if(this.has("contentType")) {
                    return this.get("contentType");
                } else {
                    return null;
                }
            }
        })
    }
);