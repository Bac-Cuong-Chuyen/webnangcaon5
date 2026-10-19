async function secureFetch(url, options={}) {
  const method=(options.method||'GET').toUpperCase();
  const headers=new Headers(options.headers||{});
  if(!['GET','HEAD','OPTIONS','TRACE'].includes(method)) {
    const response=await fetch('/api/auth/csrf',{
      credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}
    });
    if(!response.ok) throw new Error('Cannot obtain CSRF token');
    const csrf=await response.json();
    if(!csrf.headerName||!csrf.token) throw new Error('Invalid CSRF response');
    headers.set(csrf.headerName,csrf.token);
  }
  return fetch(url,{...options,credentials:'same-origin',headers});
}

/* Hợp đồng API B07 (Bắc, 16/10/2026): GET ?fromDate&toDate -> {fromDate,toDate,data:[...],totalRows,totalQuantity} */
const REPORTS = {
  import: { title:'Báo cáo nhập kho', url:'/api/reports/imports', qty:'Số lượng nhập',
    cols:['Ngày','SKU'], keys:['date','sku'], label:r=>r.date+' | '+r.sku },
  export: { title:'Báo cáo xuất kho', url:'/api/reports/exports', qty:'Số lượng xuất',
    cols:['Ngày','SKU'], keys:['date','sku'], label:r=>r.date+' | '+r.sku },
  transfer:{ title:'Báo cáo chuyển kho', url:'/api/reports/transfers', qty:'Số lượng chuyển',
    cols:['Mã kho nguồn','Mã kho đích'], keys:['fromWarehouseCode','toWarehouseCode'], label:r=>r.fromWarehouseCode+' → '+r.toWarehouseCode }
};
/* JSON mẫu trong tài liệu hợp đồng, chỉ dùng cho chế độ dữ liệu mẫu */
const DEMO = {
  import:{fromDate:'2026-10-14',toDate:'2026-10-15',totalRows:3,totalQuantity:22,data:[
    {date:'2026-10-14',sku:'B07TEST1',totalQuantity:5},{date:'2026-10-14',sku:'DELL01',totalQuantity:10},{date:'2026-10-15',sku:'DELL01',totalQuantity:7}]},
  export:{fromDate:'2026-10-14',toDate:'2026-10-15',totalRows:3,totalQuantity:9,data:[
    {date:'2026-10-14',sku:'B07TEST1',totalQuantity:2},{date:'2026-10-14',sku:'DELL01',totalQuantity:4},{date:'2026-10-15',sku:'DELL01',totalQuantity:3}]},
  transfer:{fromDate:'2026-10-14',toDate:'2026-10-15',totalRows:2,totalQuantity:13,data:[
    {fromWarehouseCode:'B07-WH-A',toWarehouseCode:'B07-WH-B',totalQuantity:10},{fromWarehouseCode:'B07-WH-B',toWarehouseCode:'B07-WH-A',totalQuantity:3}]}
};

const $ = id => document.getElementById(id);
const PAGE_SIZE=50, CHART_LIMIT=20;
let tab='import', rows=[], chart=null, isDemo=false, seq=0, page=0, currentUser=null;
let loadedFrom='', loadedTo='';
function fmt(n){return Number(n).toLocaleString('vi-VN');}
function setStatus(msg,cls){$('status').textContent=msg||'';$('status').className=cls||'';}
function clearResult(){
  rows=[];page=0;
  if(chart){chart.destroy();chart=null;}
  $('rows').textContent='';$('content').hidden=true;$('mismatch').hidden=true;$('csvBtn').disabled=true;
}
function buildTabs(){
  const keys=Object.keys(REPORTS);
  keys.forEach((k,index)=>{
    const b=document.createElement('button');
    b.type='button';b.setAttribute('role','tab');b.id='tab-'+k;
    b.setAttribute('aria-controls','reportPanel');b.textContent=REPORTS[k].title;b.dataset.k=k;
    b.onclick=()=>{tab=k;refreshTabs();load();};
    b.onkeydown=e=>{
      let next;
      if(e.key==='ArrowRight') next=(index+1)%keys.length;
      else if(e.key==='ArrowLeft') next=(index+keys.length-1)%keys.length;
      else if(e.key==='Home') next=0;
      else if(e.key==='End') next=keys.length-1;
      else return;
      e.preventDefault();$('tab-'+keys[next]).focus();$('tab-'+keys[next]).click();
    };
    $('tabs').appendChild(b);
  });
  refreshTabs();
}
function refreshTabs(){
  document.querySelectorAll('#tabs button').forEach(b=>{
    const active=b.dataset.k===tab;b.setAttribute('aria-selected',String(active));b.tabIndex=active?0:-1;
  });
  $('reportPanel').setAttribute('aria-labelledby','tab-'+tab);
}
function showLogin(show){$('loginCard').hidden=!show;}
function showUser(u){
  currentUser=u;const box=$('userBox');box.textContent='';if(!u)return;
  const name=document.createElement('span');name.textContent=(u.fullName||u.username)+(u.role?' ('+u.role+')':'');
  const button=document.createElement('button');button.type='button';button.className='ghost';button.textContent='Đăng xuất';
  button.onclick=async()=>{
    button.disabled=true;
    try{
      const response=await secureFetch('/api/auth/logout',{method:'POST'});
      if(!response.ok)throw new Error('logout');
      ++seq;showUser(null);clearResult();$('demoChk').checked=false;isDemo=false;$('demoBanner').hidden=true;
      $('reportPanel').setAttribute('aria-busy','false');setStatus('Đã đăng xuất.');showLogin(true);$('lu').focus();
    }catch(e){setStatus('Không đăng xuất được. Vui lòng thử lại.','err');button.disabled=false;}
  };
  box.append(name,button);
}
$('loginForm').onsubmit=async event=>{
  event.preventDefault();$('loginErr').textContent='';$('loginBtn').disabled=true;
  try{
    const response=await secureFetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:$('lu').value.trim(),password:$('lp').value})});
    if(!response.ok){
      $('loginErr').textContent=response.status===429?'Đăng nhập tạm thời bị giới hạn. Vui lòng thử lại sau.':
        response.status===401?'Tên đăng nhập hoặc mật khẩu không đúng.':'Không đăng nhập được (HTTP '+response.status+').';
      return;
    }
    showUser(await response.json());showLogin(false);$('lp').value='';$('applyBtn').focus();await load();
  }catch(e){$('loginErr').textContent='Không kết nối được máy chủ.';}
  finally{$('loginBtn').disabled=false;}
};
async function restoreSession(){
  $('loginBtn').disabled=true;setStatus('Đang kiểm tra phiên đăng nhập…');
  try{
    const response=await fetch('/api/auth/me',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
    if(response.status===401){showUser(null);showLogin(true);setStatus('Cần đăng nhập để xem báo cáo.');return;}
    if(!response.ok)throw new Error('session');
    showUser(await response.json());showLogin(false);await load();
  }catch(e){showUser(null);showLogin(true);setStatus('Không kiểm tra được phiên đăng nhập. Vui lòng thử tải lại trang.','err');}
  finally{$('loginBtn').disabled=false;}
}
async function load(){
  const cfg=REPORTS[tab],my=++seq,from=$('fFrom').value,to=$('fTo').value;
  isDemo=$('demoChk').checked;$('demoBanner').hidden=!isDemo;clearResult();
  $('reportPanel').setAttribute('aria-busy','false');
  $('fFrom').removeAttribute('aria-invalid');$('fTo').removeAttribute('aria-invalid');
  if(!from||!to||from>to){
    $('fFrom').setAttribute('aria-invalid','true');
    setStatus('Chọn đủ ngày và bảo đảm ngày bắt đầu không lớn hơn ngày kết thúc.','err');$('fFrom').focus();return;
  }
  if(!isDemo&&!currentUser){showLogin(true);setStatus('Cần đăng nhập để xem báo cáo.');return;}
  setStatus('Đang tải '+cfg.title.toLowerCase()+'…');$('reportPanel').setAttribute('aria-busy','true');
  try{
    let report;
    if(isDemo)report=DEMO[tab];
    else{
      const response=await fetch(cfg.url+'?'+new URLSearchParams({fromDate:from,toDate:to}),
        {credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
      if(my!==seq)return;
      if(response.status===401){showUser(null);showLogin(true);setStatus('Phiên đã hết hạn. Vui lòng đăng nhập lại.','err');return;}
      if(response.status===403){setStatus('Tài khoản hiện tại không có quyền xem báo cáo. Cần vai trò quản lý kho hoặc giám đốc.','err');return;}
      if(response.status===400){setStatus('Máy chủ từ chối khoảng ngày đã chọn.','err');return;}
      if(!response.ok)throw new Error('HTTP '+response.status);
      report=await response.json();
    }
    if(my!==seq)return;
    if(!report||!Array.isArray(report.data))throw new Error('Invalid report');
    rows=report.data;loadedFrom=report.fromDate||from;loadedTo=report.toDate||to;render(report);
  }catch(e){if(my===seq){clearResult();setStatus('Không tải được báo cáo. Kiểm tra kết nối và thử lại.','err');}}
  finally{if(my===seq)$('reportPanel').setAttribute('aria-busy','false');}
}
function render(report){
  if(!rows.length){setStatus('Không có dữ liệu trong khoảng ngày đã chọn.');return;}
  setStatus('Đã tải '+fmt(rows.length)+' dòng.');
  const sum=rows.reduce((s,r)=>s+Number(r.totalQuantity||0),0);
  $('content').hidden=false;$('csvBtn').disabled=false;$('sumQty').textContent=fmt(sum);$('sumRows').textContent=fmt(rows.length);
  const mismatch=(report.totalQuantity!==undefined&&Number(report.totalQuantity)!==sum)||
    (report.totalRows!==undefined&&Number(report.totalRows)!==rows.length);
  $('mismatch').hidden=!mismatch;
  $('mismatch').textContent=mismatch?'Cảnh báo: tổng số lượng hoặc số dòng khác số máy chủ trả về. Không dùng làm minh chứng.':'';
  const hr=document.createElement('tr');
  REPORTS[tab].cols.concat(['Số lượng']).forEach((label,index)=>{
    const h=document.createElement('th');h.scope='col';h.textContent=label;if(index===2)h.className='n';hr.appendChild(h);
  });
  $('thead').replaceChildren(hr);renderPage();
}
function renderPage(){
  const cfg=REPORTS[tab],start=page*PAGE_SIZE,visible=rows.slice(start,start+PAGE_SIZE),bars=visible.slice(0,CHART_LIMIT);
  const fragment=document.createDocumentFragment();
  visible.forEach(row=>{
    const tr=document.createElement('tr');
    cfg.keys.forEach(key=>{const td=document.createElement('td');td.textContent=row[key]??'';tr.appendChild(td);});
    const qty=document.createElement('td');qty.className='n';qty.textContent=fmt(row.totalQuantity);tr.appendChild(qty);fragment.appendChild(tr);
  });
  $('rows').replaceChildren(fragment);
  const pages=Math.ceil(rows.length/PAGE_SIZE);
  $('pageInfo').textContent='Trang '+(page+1)+'/'+pages+' · Dòng '+(start+1)+'–'+(start+visible.length)+' / '+fmt(rows.length);
  $('prevBtn').disabled=page===0;$('nextBtn').disabled=page+1>=pages;
  $('tableCaption').textContent=cfg.title+' · '+loadedFrom+' đến '+loadedTo;
  const note='Biểu đồ hiển thị '+bars.length+' dòng đầu của trang hiện tại (dòng '+(start+1)+'–'+(start+bars.length)+'). Tổng số và Excel dùng toàn bộ '+fmt(rows.length)+' dòng.';
  $('chartNote').textContent=note;$('chart').setAttribute('aria-label',cfg.title+'. '+note);
  if(chart){chart.destroy();chart=null;}
  if(typeof Chart==='undefined'){$('chartBox').hidden=true;$('chartNote').textContent='Không tải được biểu đồ. Có thể đọc bảng và xuất Excel.';return;}
  $('chartBox').hidden=false;
  const css=getComputedStyle(document.documentElement),color=css.getPropertyValue('--ac').trim(),tx=css.getPropertyValue('--tx').trim(),border=css.getPropertyValue('--br').trim();
  $('chartBox').style.height=Math.max(260,bars.length*28+100)+'px';
  chart=new Chart($('chart'),{type:'bar',
    data:{labels:bars.map(cfg.label),datasets:[{label:cfg.qty,data:bars.map(row=>Number(row.totalQuantity)),backgroundColor:color}]},
    options:{indexAxis:'y',responsive:true,maintainAspectRatio:false,animation:false,
      plugins:{title:{display:true,text:cfg.title+(isDemo?' (DỮ LIỆU MẪU)':''),color:tx},legend:{labels:{color:tx}}},
      scales:{x:{beginAtZero:true,title:{display:true,text:'Số lượng (chưa quy đổi đơn vị)',color:tx},ticks:{color:tx},grid:{color:border}},y:{ticks:{color:tx},grid:{color:border}}}}});
}
function changePage(delta){
  const next=page+delta;if(next<0||next>=Math.ceil(rows.length/PAGE_SIZE))return;
  page=next;renderPage();
  // Keep keyboard focus visible when the triggering button becomes disabled.
  if(delta<0&&$('prevBtn').disabled)$('nextBtn').focus();
  if(delta>0&&$('nextBtn').disabled)$('prevBtn').focus();
}
$('prevBtn').onclick=()=>changePage(-1);$('nextBtn').onclick=()=>changePage(1);
/* Export all loaded rows; user edits to date inputs do not relabel a previous result. */
$('csvBtn').onclick=()=>{
  if(!rows.length)return;
  if(typeof XLSX==='undefined'){setStatus('Không tải được thư viện xuất Excel. Vui lòng tải lại trang.','err');return;}
  const cfg=REPORTS[tab];
  const aoa=[cfg.cols.concat(['Số lượng'])].concat(rows.map(row=>cfg.keys.map(key=>String(row[key]??'')).concat([Number(row.totalQuantity)])));
  const sheet=XLSX.utils.aoa_to_sheet(aoa);sheet['!cols']=cfg.keys.map(()=>({wch:18})).concat([{wch:14}]);
  const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,sheet,'Bao cao');
  XLSX.writeFile(book,(isDemo?'MAU_':'')+'bao-cao-'+tab+'_'+loadedFrom+'_'+loadedTo+'.xlsx');
};
$('filterForm').onsubmit=event=>{event.preventDefault();load();};$('demoChk').onchange=load;
buildTabs();restoreSession();
