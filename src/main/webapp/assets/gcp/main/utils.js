var ajaxPost = function ajaxPost(url, data) {
  return new Promise(function (resolve, reject) {
    $.ajax({
      contentType: "application/x-www-form-urlencoded",
      dataType: "json",
      type: "POST",
      url: url,
      data: data,
      success: function(resp, textStatus, jqXHR){
        resolve(resp);
      },
      error: function(jqXHR, textStatus, errorThrown){
        reject(errorThrown);
      }
    });
  });
}

var TemplateUtils = function TemplateUtils(temaplatePath, selector) {

  // jsrender.views.converters("dateformat", function(val) {
  //   //2015-09-02T12:25:33.9500000Z
  //   return moment(val).format("dddd, MMMM Do, h:mm:ss a");
  // });

  var
    getPath = function getPath(name) {
      return temaplatePath + name + '.tmpl.html';
    },
    renderExtTemplate = function renderExtTemplate(item) {

      var file = getPath(item.name);

      console.log("renderExtTemplate with file: " + file + ", has data: " + (item.data?"YES":"NO"));

      return Promise.resolve($.get(file))
        .then(function(tmplData) {

          $.templates({
            tmpl: tmplData
          });
          var html = $.render.tmpl(item.data);

          if (item.selector) {
            selector = item.selector;
          }

          $(selector).html(html);

          return html;
        });
    }


  return {
    getPath: getPath,
    renderExtTemplate: renderExtTemplate
  }

}
