(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  let rendered = false;

  let overviewIdentity=null,overviewVersion=0,reportVersion=0;
  function resetOverview(){++overviewVersion;++reportVersion;$('overviewMetrics').replaceChildren();$('overviewStatus').textContent='Chọn khoảng ngày và bấm Xem tổng hợp.';$('productCount').textContent='';$('loadOverview').disabled=false;}
  async function dashboardRead(url){
    const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
    if(response.status===401){await AppSession.refresh();throw new Error('Phiên đã kết thúc. Vui lòng đăng nhập lại.');}
    if(response.status===403)throw new Error('Tài khoản không có quyền xem dữ liệu này.');
    if(!response.ok)throw new Error('Chưa tải được dữ liệu. Vui lòng thử lại.');return response.json();
  }
  async function productSummary(){
    const version=++overviewVersion;$('productCount').textContent='Đang tải danh mục…';
    try{const data=await dashboardRead('/api/products/search?page=0&size=1&sortBy=id&direction=asc');if(version!==overviewVersion)return;
      if(!Number.isSafeInteger(data.totalElements)||data.totalElements<0)throw new Error('Dữ liệu danh mục chưa hợp lệ.');
      $('productCount').textContent='Danh mục hiện có: '+data.totalElements.toLocaleString('vi-VN')+' sản phẩm.';
    }catch(error){if(version===overviewVersion)$('productCount').textContent=error.message||'Không kết nối được máy chủ.';}
  }
  $('refreshOverview').onclick=productSummary;
  $('overviewForm').onsubmit=async event=>{
    event.preventDefault();if(!AppSession.can('reports'))return;
    const from=$('overviewFrom').value,to=$('overviewTo').value;
    const version=++reportVersion;$('overviewMetrics').replaceChildren();
    if(!from||!to||from>to){$('overviewStatus').textContent='Ngày bắt đầu phải trước hoặc trùng ngày kết thúc.';$('overviewFrom').focus();return;}
    $('loadOverview').disabled=true;$('overviewStatus').textContent='Đang tổng hợp…';
    const types=[['imports','Nhập kho'],['exports','Xuất kho'],['transfers','Chuyển kho']];
    const results=await Promise.allSettled(types.map(async([type])=>{
      const data=await dashboardRead('/api/reports/'+type+'?'+new URLSearchParams({fromDate:from,toDate:to}));
      if(data.fromDate!==from||data.toDate!==to||!Number.isSafeInteger(data.totalQuantity)||data.totalQuantity<0)throw new Error('Dữ liệu báo cáo chưa hợp lệ.');return data.totalQuantity;
    }));
    if(version!==reportVersion)return;
    let failed=0;results.forEach((result,index)=>{const card=document.createElement('div');card.className='overview-metric';const title=document.createElement('h3'),value=document.createElement('strong'),note=document.createElement('p');title.textContent=types[index][1];value.textContent=result.status==='fulfilled'?result.value.toLocaleString('vi-VN'):'—';note.textContent=result.status==='fulfilled'?'Số lượng chưa quy đổi đơn vị':'Chưa tải được; bấm Xem tổng hợp để thử lại.';if(result.status==='rejected')++failed;card.append(title,value,note);$('overviewMetrics').append(card);});
    $('overviewStatus').textContent='Khoảng ngày '+from+' đến '+to+(failed?' · Có '+failed+' báo cáo chưa tải được.':'.');$('loadOverview').disabled=false;
  };
  function overview(account){
    const key=account?account.username+':'+account.role:null;$('overviewSection').hidden=!account;$('overviewReports').hidden=!AppSession.can('reports',account);
    if(key===overviewIdentity)return;overviewIdentity=key;resetOverview();
    const now=new Date(),localDate=d=>d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
    $('overviewTo').value=localDate(now);$('overviewFrom').value=localDate(new Date(now.getFullYear(),now.getMonth(),1));
    if(account)productSummary();
  }

  function render({account,status}) {
    rendered = true;overview(account);
    $('homeTitle').textContent = account ? 'Chào '+account.username : 'Không gian quản lý kho';
    $('homeDescription').textContent = account ? 'Truy cập công cụ làm việc theo quyền tài khoản của bạn.' : 'Đăng nhập để truy cập công cụ và dữ liệu kho của bạn.';
    $('homeSessionError').hidden = status !== 'unavailable';
    $('sessionRetry').hidden = status !== 'unavailable';
    $('homeSessionError').textContent = status === 'unavailable' ? 'Chưa kiểm tra được phiên đăng nhập. Hãy thử lại.' : '';
    $('accountSection').hidden = !account;
    $('quickSection').hidden = !account || !(AppSession.can('management') || AppSession.can('reports') || AppSession.can('audit'));
    $('welcomeSection').hidden = !!account;
    $('guideSection').hidden = !!account;
    $('logoutHome').disabled = false;
  }
  document.addEventListener('session:updated',event => render(event.detail));
  AppSessionReady.then(account => { if (!rendered) render({account,status: 'ready'}); });
  $('retrySession').onclick = () => AppSession.refresh();
  $('logoutHome').onclick = async () => {
    $('logoutHome').disabled = true; $('logoutError').textContent = '';
    try {
      const response = await AppSession.write('/api/auth/logout');
      if (!response.ok) throw new Error('logout');
      await AppSession.refresh();
      $('homeLogin').focus();
    } catch (_) { $('logoutError').textContent = 'Chưa đăng xuất được. Vui lòng thử lại.'; $('logoutHome').disabled = false; }
  };
})();
