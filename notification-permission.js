(()=>{
  if(!(location.protocol==='file:' && /Android/i.test(navigator.userAgent)) || !window.Android) return;
  const btn=document.getElementById('notifyBtn');
  if(!btn) return;

  const allowed=()=>{try{return !!Android.notificationsAllowed();}catch{return false;}};
  const setState=ok=>{btn.textContent=ok?'Reminders on':'Enable reminders';};

  window.nextMoveNotificationPermissionResult=(ok)=>{
    setState(!!ok);
    toast(ok?'Reminders enabled':'Notification permission was not granted');
  };

  setState(allowed());

  btn.onclick=()=>{
    if(allowed()){
      setState(true);
      toast('Reminders are already enabled');
      return;
    }
    Android.requestNotifications();
    toast('Choose Allow in the Android notification prompt');
  };
})();
