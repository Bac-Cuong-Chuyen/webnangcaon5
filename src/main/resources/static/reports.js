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
let tab='import', rows=[], chart=null, isDemo=false, seq=0;

function fmt(n){ return Number(n).toLocaleString('vi-VN'); }
function setStatus(msg, cls){ const s=$('status'); s.textContent=msg||''; s.className=cls||''; }

/* Xóa kết quả cũ và khóa xuất CSV (lỗi API, đổi tab, đăng xuất) */
function clearResult(){
  rows=[]; if(chart){chart.destroy();chart=null;}
  $('content').hidden=true; $('mismatch').hidden=true; $('csvBtn').disabled=true;
}

function buildTabs(){
  Object.entries(REPORTS).forEach(([k,v])=>{
    const b=document.createElement('button');
    b.type='button'; b.role='tab'; b.textContent=v.title; b.dataset.k=k;
    b.onclick=()=>{ tab=k; refreshTabs(); clearResult(); load(); };
    $('tabs').appendChild(b);
  });
  refreshTabs();
}
function refreshTabs(){ document.querySelectorAll('#tabs button').forEach(b=>b.setAttribute('aria-selected', b.dataset.k===tab)); }

function showLogin(show){ $('loginCard').hidden=!show; }
function showUser(u){
  const box=$('userBox'); box.textContent='';
  if(!u) return;
  const s=document.createElement('span');
  s.textContent=(u.fullName||u.username)+(u.role?' ('+u.role+') ':' ');
  const b=document.createElement('button');
  b.type='button'; b.className='ghost'; b.textContent='Đăng xuất';
  b.onclick=async()=>{ try{const res=await secureFetch('/api/auth/logout',{method:'POST',credentials:'same-origin'}); if(!res.ok){setStatus('Dang xuat that bai (HTTP '+res.status+').','err');return;}}catch(e){setStatus('Khong ket noi duoc may chu.','err');return;} ++seq;
    showUser(null); clearResult(); setStatus('Đã đăng xuất.'); showLogin(true); };
  box.append(s,b);
}
$('loginBtn').onclick=async()=>{
  $('loginErr').textContent='';
  try{
    const res=await secureFetch('/api/auth/login',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:$('lu').value,password:$('lp').value})});
    if(!res.ok){ $('loginErr').textContent=(await res.text())||'Đăng nhập thất bại.'; return; }
    showUser(await res.json()); showLogin(false); $('lp').value=''; load();
  }catch(e){ $('loginErr').textContent='Không kết nối được máy chủ.'; }
};

async function load(){
  const cfg=REPORTS[tab], my=++seq;
  const from=$('fFrom').value, to=$('fTo').value;
  isDemo=$('demoChk').checked;
  $('demoBanner').hidden=!isDemo;
  clearResult();
  if(!from||!to){ setStatus('Chọn đủ ngày bắt đầu và ngày kết thúc.','err'); return; }
  if(from>to){ setStatus('Ngày bắt đầu không được lớn hơn ngày kết thúc.','err'); return; }
  setStatus('Đang tải '+cfg.title.toLowerCase()+'...');
  try{
    let report;
    if(isDemo) report=DEMO[tab];
    else{
      const q=new URLSearchParams({fromDate:from,toDate:to});
      const res=await fetch(cfg.url+'?'+q,{credentials:'same-origin',headers:{Accept:'application/json'}});
      if(my!==seq) return;
      if(res.status===401){ showLogin(true); setStatus('Cần đăng nhập để xem báo cáo.','err'); return; }
      if(res.status===400){ setStatus('Máy chủ từ chối khoảng ngày đã chọn. Kiểm tra lại ngày bắt đầu và ngày kết thúc.','err'); return; }
      if(!res.ok){ setStatus('Không tải được báo cáo (HTTP '+res.status+').','err'); return; }
      report=await res.json();
    }
    if(my!==seq) return;
    rows=Array.isArray(report.data)?report.data:[];
    render(report);
  }catch(e){ if(my===seq){ clearResult(); setStatus('Không kết nối được máy chủ hoặc dữ liệu trả về sai định dạng.','err'); } }
}

function render(report){
  const cfg=REPORTS[tab];
  if(!rows.length){ setStatus('Không có dữ liệu trong khoảng ngày đã chọn.'); return; }
  setStatus('');
  const sum=rows.reduce((s,r)=>s+Number(r.totalQuantity||0),0);
  $('content').hidden=false; $('csvBtn').disabled=false;
  $('sumQty').textContent=fmt(sum); $('sumRows').textContent=fmt(rows.length);
  const mm=$('mismatch');
  if(report.totalQuantity!==undefined && Number(report.totalQuantity)!==sum){
    mm.hidden=false; mm.textContent='Cảnh báo: tổng các dòng ('+fmt(sum)+') khác tổng máy chủ trả về ('+fmt(report.totalQuantity)+'). Không dùng làm minh chứng.';
  } else mm.hidden=true;

  const th=$('thead'); th.textContent='';
  const hr=document.createElement('tr');
  cfg.cols.concat(['Số lượng']).forEach((c,i)=>{ const h=document.createElement('th'); h.textContent=c; if(i===2) h.className='n'; hr.appendChild(h); });
  th.appendChild(hr);
  const tb=$('rows'); tb.textContent='';
  rows.forEach(r=>{
    const tr=document.createElement('tr');
    cfg.keys.forEach(k=>{ const td=document.createElement('td'); td.textContent=r[k]??''; tr.appendChild(td); });
    const q=document.createElement('td'); q.className='n'; q.textContent=fmt(r.totalQuantity); tr.appendChild(q);
    tb.appendChild(tr);
  });

  const css=getComputedStyle(document.documentElement);
  const c1=css.getPropertyValue('--ac').trim(), tx=css.getPropertyValue('--tx').trim(), br=css.getPropertyValue('--br').trim();
  $('chartBox').style.height=Math.min(900,Math.max(240,rows.length*34+90))+'px';
  chart=new Chart($('chart'),{
    type:'bar',
    data:{labels:rows.map(cfg.label),datasets:[{label:cfg.qty,data:rows.map(r=>Number(r.totalQuantity)),backgroundColor:c1}]},
    options:{indexAxis:'y',responsive:true,maintainAspectRatio:false,
      plugins:{title:{display:true,text:cfg.title+(isDemo?' (DỮ LIỆU MẪU)':''),color:tx},legend:{labels:{color:tx}}},
      scales:{x:{beginAtZero:true,title:{display:true,text:'Số lượng (chưa quy đổi đơn vị)',color:tx},ticks:{color:tx},grid:{color:br}},
              y:{ticks:{color:tx},grid:{color:br}}}}
  });
}

/* Xuất .xlsx: không phụ thuộc dấu phân cách hay mã hóa của Excel. Chuỗi luôn là ô văn bản
   (không bị hiểu thành công thức); ngày giữ dạng chữ yyyy-MM-dd. Cùng tập dữ liệu với biểu đồ. */
$('csvBtn').onclick=()=>{
  if(!rows.length) return;
  if(typeof XLSX==='undefined'){ setStatus('Không tải được thư viện xuất Excel (kiểm tra kết nối mạng).','err'); return; }
  const cfg=REPORTS[tab];
  const aoa=[cfg.cols.concat(['Số lượng'])].concat(rows.map(r=>cfg.keys.map(k=>String(r[k]??'')).concat([Number(r.totalQuantity)])));
  const ws=XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols']=cfg.keys.map(()=>({wch:18})).concat([{wch:14}]);
  const wb=XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb,ws,'Bao cao');
  XLSX.writeFile(wb,(isDemo?'MAU_':'')+'bao-cao-'+tab+'_'+$('fFrom').value+'_'+$('fTo').value+'.xlsx');
};

$('applyBtn').onclick=load;
$('demoChk').onchange=load;
buildTabs(); load();
