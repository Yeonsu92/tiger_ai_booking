
// injection예시 : textContent변경
(function waitAndAttach() {
  function attachCustomClickListener() {
    const alarmLink = document.querySelector('a._menu.alarm.pop-inline[data-item-menu="알림"]');
    if (alarmLink) {
      const clone = alarmLink.cloneNode(true);
      alarmLink.replaceWith(clone);
      clone.childNodes.forEach(function (node) {
        if (node.nodeType === Node.TEXT_NODE) {
          node.textContent = 'injected!';
        }
      });
    } else {
      setTimeout(attachCustomClickListener, 100);
    }
  }
  attachCustomClickListener();
})();


