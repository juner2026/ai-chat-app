(function(){
  if (window.__aiChatNativeHooked) return; window.__aiChatNativeHooked = true;
  function save(name, base64){
    try { if (window.AndroidSaver && window.AndroidSaver.saveBase64) window.AndroidSaver.saveBase64(name || ('file_' + Date.now()), base64); } catch(e){}
  }
  var oldClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    try {
      var href = this.href || '';
      var name = this.getAttribute('download') || '';
      if (href.indexOf('blob:') === 0) {
        fetch(href).then(function(r){ return r.blob(); }).then(function(bl){
          var fr = new FileReader();
          fr.onload = function(){
            var s = String(fr.result); var i = s.indexOf(',');
            save(name, s.substring(i + 1));
          };
          fr.readAsDataURL(bl);
        }).catch(function(){});
        return;
      }
      if (href.indexOf('data:') === 0) {
        var i2 = href.indexOf(',');
        var meta = href.substring(5, i2);
        var body = href.substring(i2 + 1);
        if (meta.indexOf('base64') === -1) { try { body = btoa(decodeURIComponent(body)); } catch(e){} }
        save(name, body);
        return;
      }
    } catch(e){}
    return oldClick.apply(this, arguments);
  };
})();
