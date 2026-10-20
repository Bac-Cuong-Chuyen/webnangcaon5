(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const descriptions={
    WAREHOUSE_STAFF:'Lập phiếu nhập/xuất, tra cứu tồn và xem phiếu thuộc tài khoản của mình.',
    WAREHOUSE_MANAGER:'Quản lý danh mục, duyệt phiếu do người khác lập, chuyển kho, đặt ngưỡng tồn và xem báo cáo.',
    DIRECTOR:'Tra cứu dữ liệu kho, phiếu và báo cáo; không thực hiện thao tác ghi nghiệp vụ kho.',
    ADMIN:'Quản lý tài khoản, cấp vai trò, danh mục hệ thống và nhật ký. Không thay quyền quản lý kho khi duyệt hoặc chuyển kho.'
  };
  let user=null,identity=null,page=0,lookup='',sequence=0,editing=null,writing=false,loading=false,openRequest=0;
  const isAdmin=()=>user?.role==='ADMIN';
  const safeAccount=raw=>({id:raw.id,username:raw.username,role:raw.role});
  function message(text,error=false){$('accountMessage').textContent=text;$('accountMessage').className=error?'error':'';}
  function clear(){ $('accountRows').replaceChildren();$('accountPrev').disabled=$('accountNext').disabled=true;$('accountPage').textContent='Chưa có dữ liệu'; }
  async function failure(response){
    if(response.status===401){await AppSession.refresh();return 'Phiên đã kết thúc. Vui lòng đăng nhập lại.';}
    if(response.status===403)return 'Tài khoản hiện tại không có quyền quản lý người dùng.';
    if(response.status===404)return 'Không tìm thấy tài khoản.';
    if(response.status===409)return 'Thông tin tài khoản đang xung đột. Hãy tải lại danh sách.';
    return 'Không thực hiện được yêu cầu. Vui lòng thử lại.';
  }
  async function read(url){const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});if(!response.ok)throw new Error(await failure(response));return response;}
  async function load(){
    const request=++sequence;if(!isAdmin()){clear();return;}
    clear();loading=true;message('Đang tải tài khoản…');$('accountsContent').setAttribute('aria-busy','true');
    try{
      const response=await read('/api/user-accounts'+(lookup?'/'+encodeURIComponent(lookup):'?'+new URLSearchParams({page:String(page),size:$('accountSize').value})));
      const data=await response.json();if(request!==sequence)return;
      const rows=lookup?[data]:data;if(!Array.isArray(rows))throw new Error('Dữ liệu tài khoản chưa hợp lệ.');
      draw(rows.map(safeAccount));$('accountPrev').disabled=!!lookup||page===0;$('accountNext').disabled=!!lookup||response.headers.get('X-Has-Next')!=='true';
      $('accountPage').textContent=lookup?'Kết quả ID '+lookup:'Trang '+(page+1)+' · '+rows.length+' tài khoản';message(rows.length?'Đã tải '+rows.length+' tài khoản.':'Không có tài khoản phù hợp.');
    }catch(error){if(request===sequence){clear();message(error instanceof TypeError?'Không kết nối được máy chủ.':error.message,true);}}
    finally{if(request===sequence){loading=false;$('accountsContent').setAttribute('aria-busy','false');}}
  }
  function draw(rows){
    const fragment=document.createDocumentFragment();
    for(const row of rows){const tr=document.createElement('tr');
      for(const value of [row.id,row.username]){const td=document.createElement('td');td.textContent=value??'—';tr.append(td);}
      const role=document.createElement('td'),badge=document.createElement('span');badge.className='account-role';badge.textContent=AppSession.label(row);role.append(badge);tr.append(role);
      const action=document.createElement('td');
      if(row.username===user.username){const own=document.createElement('span');own.className='account-self';own.textContent='Tài khoản đang sử dụng';action.append(own);}
      else{const button=document.createElement('button');button.type='button';button.className='ghost';button.textContent='Cấp vai trò';button.setAttribute('aria-label','Cấp vai trò cho '+row.username);button.onclick=()=>openRole(row.id,button);action.append(button);}
      tr.append(action);fragment.append(tr);
    }$('accountRows').replaceChildren(fragment);
  }
  function roleSelection(){
    const role=$('newRole').value;$('roleDescription').textContent=descriptions[role]||'Chọn một trong bốn vai trò của hệ thống.';
    $('saveRole').disabled=writing||!editing||!descriptions[role]||role===editing.role;
  }
  async function openRole(id,button){
    if(!isAdmin()||writing)return;const request=++openRequest,key=identity;button.disabled=true;
    try{
      const response=await read('/api/user-accounts/'+encodeURIComponent(id)),row=safeAccount(await response.json());
      if(request!==openRequest||key!==identity)return;
      if(row.username===user.username){message('Tài khoản đang sử dụng không đổi vai trò tại màn hình này.',true);return;}
      editing=row;$('roleTarget').textContent=row.username+' · ID '+row.id;$('roleCurrent').textContent='Vai trò hiện tại: '+AppSession.label(row);
      $('newRole').value=descriptions[row.role]?row.role:'';$('roleError').textContent='';roleSelection();$('roleDialog').showModal();$('newRole').focus();
    }catch(error){if(key===identity)message(error instanceof TypeError?'Không kết nối được máy chủ.':error.message,true);}
    finally{button.disabled=false;}
  }
  $('newRole').onchange=()=>{ $('roleError').textContent='';roleSelection(); };
  $('roleForm').onsubmit=async event=>{
    event.preventDefault();const role=$('newRole').value;
    if(writing||!isAdmin()||!editing||editing.username===user.username||!descriptions[role]||role===editing.role)return;
    const account=editing,key=identity;writing=true;$('saveRole').disabled=$('cancelRole').disabled=$('newRole').disabled=true;$('roleForm').setAttribute('aria-busy','true');$('roleError').textContent='';
    try{
      // Refresh the account before PUT so a stale list cannot silently revert a recent rename/role edit.
      const current=await read('/api/user-accounts/'+account.id),latest=safeAccount(await current.json());
      if(key!==identity)return;
      if(latest.username!==account.username||latest.role!==account.role)throw new Error('Thông tin vừa thay đổi. Đóng hộp thoại và mở lại trước khi cấp vai trò.');
      const response=await AppSession.write('/api/user-accounts/'+account.id,{username:account.username,role},'PUT');
      if(!response.ok)throw new Error(await failure(response));
      if(key!==identity)return;
      $('roleDialog').close();editing=null;await load();message('Đã cấp vai trò '+AppSession.label({role})+' cho '+account.username+'. Người dùng cần đăng nhập lại.');
    }catch(error){if(key===identity)$('roleError').textContent=error instanceof TypeError?'Chưa xác nhận được kết quả. Tải lại tài khoản trước khi gửi lại.':error.message;}
    finally{writing=false;$('cancelRole').disabled=$('newRole').disabled=false;$('roleForm').setAttribute('aria-busy','false');roleSelection();}
  };
  $('cancelRole').onclick=()=>{if(!writing){$('roleDialog').close();editing=null;}};
  $('roleDialog').addEventListener('cancel',event=>{if(writing)event.preventDefault();});
  $('accountSearch').onsubmit=event=>{event.preventDefault();const value=$('accountId').value.trim();if(value&&(!/^[1-9]\d*$/.test(value)||!Number.isSafeInteger(Number(value)))){message('ID phải là số nguyên dương hợp lệ.',true);$('accountId').focus();return;}lookup=value;page=0;load();};
  $('accountReset').onclick=()=>{lookup='';page=0;$('accountId').value='';load();};$('accountSize').onchange=()=>{page=0;load();};
  $('accountPrev').onclick=()=>{if(!loading&&page>0){--page;load();}};$('accountNext').onclick=()=>{if(!loading){++page;load();}};
  function session({account,status}){
    user=account;const key=user?user.username+':'+user.role:null;
    $('accountsGate').hidden=!!user;$('accountsContent').hidden=!isAdmin();
    $('accountsAccess').textContent=status==='unavailable'?'Chưa kiểm tra được phiên. Hãy tải lại trang.':user&&!isAdmin()?'Chỉ quản trị viên được quản lý tài khoản.':'';
    if(key===identity)return;identity=key;++sequence;++openRequest;editing=null;clear();loading=false;$('roleTarget').textContent='';$('newRole').value='';
    if($('roleDialog').open)$('roleDialog').close();if(isAdmin())load();
  }
  let received=false;document.addEventListener('session:updated',event=>{received=true;session(event.detail);});AppSessionReady.then(account=>{if(!received)session({account,status:'ready'});});
})();
