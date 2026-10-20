(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const configs={imports:{title:'Phiếu nhập kho',create:'Lập phiếu nhập',list:'/api/import-receipts',lines:'/api/import-details',write:'/api/imports'},exports:{title:'Phiếu xuất kho',create:'Lập phiếu xuất',list:'/api/export-receipts',lines:'/api/export-details',write:'/api/exports'},transfers:{title:'Chuyển kho',create:'Lập phiếu chuyển',write:'/api/transfers'}};
  const states={DRAFT:'Nháp',APPROVED:'Đã duyệt',COMPLETED:'Hoàn tất',CANCELLED:'Đã hủy'};
  let kind='imports',page=0,lookup='',mode='receipts',user=null,identity=null,sequence=0,loading=false,writing=false,pending=null,lastTransfer=null;
  const canCreate=()=>kind==='transfers'?user?.role==='WAREHOUSE_MANAGER':['ADMIN','WAREHOUSE_MANAGER','WAREHOUSE_STAFF'].includes(user?.role);
  const canApprove=row=>user?.role==='WAREHOUSE_MANAGER'&&row.status==='DRAFT'&&!!row.createdBy&&row.createdBy!==user.username;
  const positive=value=>/^\d+$/.test(String(value))&&Number.isSafeInteger(Number(value))&&Number(value)>0;
  function message(text,error=false,id='receiptMessage'){ $(id).textContent=text;$(id).className=error?'error':''; }
  function clear(){ $('receiptRows').replaceChildren();$('receiptPrev').disabled=$('receiptNext').disabled=true;$('receiptPage').textContent='Chưa có dữ liệu'; }
  async function errorMessage(response){
    if(response.status===401){await AppSession.refresh();return 'Phiên đã kết thúc. Vui lòng đăng nhập lại.';}
    if(response.status===403)return 'Tài khoản không có quyền thực hiện thao tác này.';
    if(response.status===404)return 'Không tìm thấy bản ghi hoặc bản ghi không thuộc phạm vi được xem.';
    if([400,409].includes(response.status)){
      try{const data=await response.json();if(typeof data.message==='string')return data.message.slice(0,400);}catch(_){}
      return 'Phiếu chưa đáp ứng điều kiện xử lý. Hãy đối chiếu trạng thái, người lập và số lượng tồn.';
    }
    return 'Máy chủ chưa thực hiện được yêu cầu. Vui lòng thử lại.';
  }
  async function read(url){
    const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
    if(!response.ok)throw new Error(await errorMessage(response));return response;
  }
  function switchKind(next,force=false){
    if(writing&&!force)return;
    if(next==='transfers'&&user?.role!=='WAREHOUSE_MANAGER')return;
    kind=next;page=0;lookup='';$('receiptLookup').value='';$('writeNotice').textContent='';clear();++sequence;
    document.querySelectorAll('[data-kind]').forEach(button=>{const active=button.dataset.kind===kind;button.setAttribute('aria-selected',String(active));button.tabIndex=active?0:-1;});
    $('receiptPanel').setAttribute('aria-labelledby','tab-'+kind);$('receiptTitle').textContent=configs[kind].title;
    $('createReceipt').textContent=configs[kind].create;$('createReceipt').hidden=!canCreate();
    $('receiptListing').hidden=kind==='transfers';$('transferPanel').hidden=kind!=='transfers';
    $('receiptNote').textContent=kind==='transfers'?'Chỉ quản lý kho được lập và xử lý phiếu chuyển.':user?.role==='WAREHOUSE_STAFF'?'Bạn chỉ xem được phiếu thuộc tài khoản của mình.':'Người lập phiếu không tự duyệt phiếu của mình.';
    if(kind!=='transfers')load();else{$('receiptPanel').setAttribute('aria-busy','false');loading=false;}
  }
  async function load(){
    const request=++sequence,currentKind=kind,currentMode=mode;
    if(!user||!AppSession.can('receipts',user)||kind==='transfers'){clear();return;}
    clear();message('Đang tải…');loading=true;$('receiptPanel').setAttribute('aria-busy','true');
    try{
      const config=configs[kind],base=mode==='lines'?config.lines:config.list;
      const url=lookup?base+'/'+encodeURIComponent(lookup):base+'?'+new URLSearchParams({page:String(page),size:$('receiptSize').value});
      const response=await read(url),data=await response.json();if(request!==sequence)return;
      const rows=lookup?[data]:data;if(!Array.isArray(rows))throw new Error('Dữ liệu phản hồi không hợp lệ.');
      draw(rows,currentKind,currentMode);
      $('receiptPrev').disabled=!!lookup||page===0;$('receiptNext').disabled=!!lookup||response.headers.get('X-Has-Next')!=='true';
      $('receiptPage').textContent=lookup?'Kết quả ID '+lookup:'Trang '+(page+1)+' · '+rows.length+' bản ghi';message(rows.length?'Đã tải '+rows.length+' bản ghi.':'Không có bản ghi phù hợp.');
    }catch(error){if(request===sequence){clear();message(error.message||'Không kết nối được máy chủ.',true);}}
    finally{if(request===sequence){loading=false;$('receiptPanel').setAttribute('aria-busy','false');}}
  }
  const canEditDraft=row=>row.status==='DRAFT'&&(['ADMIN','WAREHOUSE_MANAGER'].includes(user?.role)||(user?.role==='WAREHOUSE_STAFF'&&row.createdBy===user.username));
  function draw(rows,currentKind,currentMode){
    const line=currentMode==='lines',keys=line?['id','receiptId','productId','quantity']:['id','receiptCode','receiptDate','warehouseId','status','createdBy','approvedBy'];
    const headers=line?['ID dòng','ID phiếu','Sản phẩm','Số lượng']:['ID phiếu','Mã phiếu','Ngày lập','Kho','Trạng thái','Người lập','Người duyệt'];
    $('receiptCaption').textContent=(line?'Dòng hàng · ':'')+configs[currentKind].title;
    const head=document.createElement('tr');for(const label of headers.concat(line?[]:['Thao tác'])){const th=document.createElement('th');th.scope='col';th.textContent=label;head.append(th);}$('receiptHead').replaceChildren(head);
    const fragment=document.createDocumentFragment();
    for(const row of rows){const tr=document.createElement('tr');
      for(const key of keys){const td=document.createElement('td');
        if(key==='status'){const span=document.createElement('span');span.className='receipt-status'+(row.status==='DRAFT'?' draft':'');span.textContent=states[row.status]||row.status||'—';td.append(span);}
        else td.textContent=row[key]??'—';if(['warehouseId','productId'].includes(key))EntityLabels.mark(td,key==='warehouseId'?'warehouses':'products',row[key]);tr.append(td);
      }
      if(!line){const td=document.createElement('td'),view=document.createElement('button');view.type='button';view.className='ghost';view.textContent='Thông tin';view.onclick=()=>showDetail(row);td.append(view);
        if(canEditDraft(row)){const edit=document.createElement('button');edit.type='button';edit.className='ghost';edit.textContent='Sửa thông tin';edit.setAttribute('aria-label','Sửa phiếu '+(row.receiptCode||row.id));edit.onclick=()=>openDraft(row);td.append(edit);}
        if(canApprove(row)){const approve=document.createElement('button');approve.type='button';approve.textContent='Duyệt';approve.setAttribute('aria-label','Duyệt phiếu '+(row.receiptCode||row.id));approve.onclick=()=>confirmAction({type:'approve',id:row.id,row,kind:currentKind});td.append(approve);}
        tr.append(td);
      }fragment.append(tr);
    }$('receiptRows').replaceChildren(fragment);EntityLabels.hydrate($('receiptRows'));
  }

  let draft=null,draftBusy=false,draftVersion=0;
  function openDraft(row){
    if(writing||draftBusy||!canEditDraft(row))return;
    draft={row:{...row},kind,identity};++draftVersion;
    $('draftTitle').textContent='Sửa phiếu nháp ID '+row.id;$('draftCode').value=row.receiptCode||'';$('draftDate').value=row.receiptDate||'';$('draftError').textContent='';
    $('draftDialog').showModal();$('draftCode').focus();
  }
  $('cancelDraft').onclick=()=>{if(!draftBusy)$('draftDialog').close();};
  $('draftDialog').addEventListener('cancel',event=>{if(draftBusy)event.preventDefault();});
  $('draftDialog').addEventListener('close',()=>{draft=null;++draftVersion;});
  $('draftForm').onsubmit=async event=>{
    event.preventDefault();if(draftBusy||!draft||!canEditDraft(draft.row))return;
    const current=draft,version=draftVersion,code=$('draftCode').value.trim(),date=$('draftDate').value;
    if(!code||!/^\d{4}-\d{2}-\d{2}$/.test(date)){$('draftError').textContent='Nhập mã và ngày lập hợp lệ.';return;}
    const valid=()=>version===draftVersion&&current.identity===identity;
    draftBusy=true;$('saveDraft').disabled=$('cancelDraft').disabled=true;$('draftForm').setAttribute('aria-busy','true');$('draftError').textContent='';
    try{
      const url=configs[current.kind].list+'/'+current.row.id,response=await read(url),fresh=await response.json();
      if(!valid())return;
      if(!canEditDraft(fresh)||['receiptCode','receiptDate','warehouseId','status','createdBy'].some(key=>fresh[key]!==current.row[key]))throw new Error('Phiếu vừa thay đổi. Đóng hộp thoại và tải lại danh sách trước khi sửa.');
      const result=await AppSession.write(url,{receiptCode:code,receiptDate:date},'PUT');
      if(!result.ok)throw new Error(await errorMessage(result));
      if(!valid())return;
      $('draftDialog').close();await load();$('writeNotice').textContent='Đã cập nhật thông tin phiếu ID '+current.row.id+'.';
    }catch(error){if(valid())$('draftError').textContent=error instanceof TypeError?'Chưa xác nhận được kết quả. Tải lại phiếu trước khi thử lại.':error.message||'Chưa lưu được thông tin.';}
    finally{draftBusy=false;$('saveDraft').disabled=$('cancelDraft').disabled=false;$('draftForm').setAttribute('aria-busy','false');}
  };

  function fillData(node,row,transfer=false){
    node.replaceChildren();
    const fields=transfer?[['id','ID phiếu'],['transferCode','Mã chuyển'],['transferDate','Ngày lập'],['fromWarehouseId','ID kho nguồn'],['toWarehouseId','ID kho đích'],['status','Trạng thái'],['createdBy','Người lập'],['approvedBy','Người duyệt']]:[['id','ID phiếu'],['receiptCode','Mã phiếu'],['receiptDate','Ngày lập'],['warehouseId','ID kho'],['status','Trạng thái'],['createdBy','Người lập'],['approvedBy','Người duyệt']];
    for(const [key,label] of fields){const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=key==='status'?(states[row[key]]||row[key]||'—'):(row[key]??'—');if(['warehouseId','fromWarehouseId','toWarehouseId'].includes(key))EntityLabels.mark(dd,'warehouses',row[key]);node.append(dt,dd);}
  }
  let detailVersion=0,detailScan=null;
  function showDetail(row){$('detailTitle').textContent=configs[kind].title+' · ID '+row.id;fillData($('detailData'),row);EntityLabels.hydrate($('detailData'));++detailVersion;detailScan={id:row.id,kind,page:0,count:0};$('detailLines').replaceChildren();$('detailScanStatus').textContent='Chưa đối chiếu dòng hàng. Mỗi lượt kiểm tra tối đa 100 dòng trong phạm vi được xem.';$('scanDetailLines').disabled=false;$('receiptDetail').showModal();}

  $('receiptDetail').addEventListener('close',()=>{++detailVersion;detailScan=null;});
  $('scanDetailLines').onclick=async()=>{
    const scan=detailScan,version=detailVersion;if(!scan)return;
    $('scanDetailLines').disabled=true;$('detailScanStatus').textContent='Đang đối chiếu…';
    try{
      const response=await read(configs[scan.kind].lines+'?'+new URLSearchParams({page:String(scan.page),size:'100'})),rows=await response.json();
      if(version!==detailVersion||!$('receiptDetail').open)return;
      if(!Array.isArray(rows))throw new Error('Dữ liệu dòng hàng chưa hợp lệ.');
      for(const line of rows.filter(line=>String(line.receiptId)===String(scan.id))){
        const tr=document.createElement('tr');for(const key of ['id','productId','quantity']){const td=document.createElement('td');td.textContent=line[key]??'—';if(key==='productId')EntityLabels.mark(td,'products',line[key]);tr.append(td);}$('detailLines').append(tr);++scan.count;
      }
      ++scan.page;scan.checked=(scan.checked||0)+rows.length;const more=response.headers.get('X-Has-Next')==='true';
      $('detailScanStatus').textContent='Đã đối chiếu '+scan.checked+' dòng, tìm thấy '+scan.count+' dòng thuộc phiếu này. '+(more?'Chưa tìm hết; bấm tiếp để kiểm tra trang sau.':'Đã hết danh sách trong phạm vi tài khoản tại lần kiểm tra này.');
      $('scanDetailLines').disabled=!more;EntityLabels.hydrate($('detailLines'));
    }catch(error){if(version===detailVersion){$('detailScanStatus').textContent=error.message||'Chưa tải được. Bấm lại để thử.';$('scanDetailLines').disabled=false;}}
  };

  $('closeDetail').onclick=()=>$('receiptDetail').close();

  let pickerGeneration=0,productRequest=0,productPage=0,productQuery='',warehousePage=0,warehouseBusy=false;
  function resetPicker(){++pickerGeneration;++productRequest;productPage=0;productQuery='';warehousePage=0;warehouseBusy=false;
    for(const id of ['receiptProduct','receiptWarehouse','transferFrom','transferTo'])$(id).replaceChildren(new Option(id==='receiptProduct'?'Chọn sản phẩm':'Chọn kho',''));
    $('productKeyword').value='';$('productStatus').textContent='';$('warehouseStatus').textContent='';$('moreWarehouses').disabled=false;
    $('productPrev').disabled=$('productNext').disabled=true;
  }
  async function searchProducts(){
    const generation=pickerGeneration,request=++productRequest;
    $('receiptProduct').replaceChildren(new Option('Chọn sản phẩm',''));$('productPrev').disabled=$('productNext').disabled=true;$('productStatus').textContent='Đang tìm…';
    try{
      const response=await read('/api/products/search?'+new URLSearchParams({keyword:productQuery,page:String(productPage),size:'20',sortBy:'id',direction:'asc'})),data=await response.json();
      if(generation!==pickerGeneration||request!==productRequest||!$('createDialog').open)return;
      if(!Array.isArray(data.content))throw new Error('Dữ liệu sản phẩm không hợp lệ.');
      for(const row of data.content)$('receiptProduct').append(new Option(row.sku+' · '+row.name+' ('+row.unit+')',String(row.id)));
      $('productStatus').textContent='Trang '+(productPage+1)+' · '+data.totalElements+' kết quả';$('productPrev').disabled=productPage===0;$('productNext').disabled=data.last!==false;
    }catch(error){if(generation===pickerGeneration&&request===productRequest)$('productStatus').textContent=error.message||'Không tải được sản phẩm. Bấm Tìm để thử lại.';}
  }
  async function loadWarehouses(){
    if(warehouseBusy)return;warehouseBusy=true;const generation=pickerGeneration;$('moreWarehouses').disabled=true;$('warehouseStatus').textContent='Đang tải kho…';
    try{
      const response=await read('/api/warehouses?'+new URLSearchParams({page:String(warehousePage),size:'100'})),rows=await response.json();
      if(generation!==pickerGeneration||!$('createDialog').open)return;
      if(!Array.isArray(rows))throw new Error('Dữ liệu kho không hợp lệ.');
      for(const id of ['receiptWarehouse','transferFrom','transferTo'])for(const row of rows)$(id).append(new Option(row.code+' · '+row.name,String(row.id)));
      ++warehousePage;$('warehouseStatus').textContent='Đã tải '+($('receiptWarehouse').options.length-1)+' kho.';$('moreWarehouses').disabled=response.headers.get('X-Has-Next')!=='true';
    }catch(error){if(generation===pickerGeneration){$('warehouseStatus').textContent=error.message||'Không tải được kho. Bấm tải thêm để thử lại.';$('moreWarehouses').disabled=false;}}
    finally{if(generation===pickerGeneration)warehouseBusy=false;}
  }
  $('findProduct').onclick=()=>{productQuery=$('productKeyword').value.trim();productPage=0;searchProducts();};
  $('productKeyword').oninput=()=>{++productRequest;$('receiptProduct').replaceChildren(new Option('Bấm Tìm để chọn sản phẩm',''));$('productPrev').disabled=$('productNext').disabled=true;$('productStatus').textContent='Bấm Tìm để cập nhật kết quả.';};
  $('productKeyword').onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();$('findProduct').click();}};
  $('productPrev').onclick=()=>{if(productPage>0){--productPage;searchProducts();}};
  $('productNext').onclick=()=>{++productPage;searchProducts();};
  $('moreWarehouses').onclick=loadWarehouses;
  $('createDialog').addEventListener('close',resetPicker);

  $('createReceipt').onclick=()=>{
    if(writing||!canCreate())return;
    $('createForm').reset();$('createError').textContent='';$('createTitle').textContent=configs[kind].create;
    const transfer=kind==='transfers';$('singleWarehouse').hidden=transfer;$('receiptWarehouse').required=!transfer;
    $('transferWarehouses').hidden=!transfer;$('transferFrom').required=$('transferTo').required=transfer;
    resetPicker();$('createDialog').showModal();searchProducts();loadWarehouses();$('receiptCode').focus();
  };
  function setWriting(busy){
    writing=busy;
    for(const id of ['submitReceipt','dismissCreate','submitConfirm','dismissConfirm','createReceipt','completeTransfer','cancelTransfer'])$(id).disabled=busy;
    $('createForm').setAttribute('aria-busy',String(busy));
  }
  function transferResult(row){lastTransfer=row;$('lastTransfer').hidden=false;fillData($('lastTransferData'),row,true);}
  $('createForm').onsubmit=async event=>{
    event.preventDefault();if(writing||!canCreate())return;
    const code=$('receiptCode').value.trim(),product=$('receiptProduct').value,qty=$('receiptQuantity').value,transfer=kind==='transfers';
    if(!code){message('Nhập mã phiếu.',true,'createError');$('receiptCode').focus();return;}
    if(!positive(product)||!positive(qty)||Number(qty)>2147483647){message('Chọn sản phẩm và nhập số lượng nguyên dương hợp lệ.',true,'createError');return;}
    const payload=transfer?{transferCode:code,fromWarehouseId:Number($('transferFrom').value),toWarehouseId:Number($('transferTo').value),productId:Number(product),quantity:Number(qty)}:{receiptCode:code,warehouseId:Number($('receiptWarehouse').value),productId:Number(product),quantity:Number(qty)};
    if(transfer? !positive(payload.fromWarehouseId)||!positive(payload.toWarehouseId):!positive(payload.warehouseId)){message('Chọn kho hợp lệ.',true,'createError');return;}
    if(transfer&&payload.fromWarehouseId===payload.toWarehouseId){message('Kho nguồn và kho đích phải khác nhau.',true,'createError');$('transferTo').focus();return;}
    const key=identity,savedKind=kind;setWriting(true);message('',false,'createError');
    try{
      const response=await AppSession.write(configs[kind].write,payload);if(!response.ok)throw new Error(await errorMessage(response));
      let id;
      if(transfer){const row=await response.json();if(key!==identity)return;transferResult(row);id=row.id;}
      else{const text=await response.text();id=text.match(/\bid=(\d+)/)?.[1];}
      if(key!==identity||savedKind!==kind)return;
      $('createDialog').close();if(!transfer)await load();
      $('writeNotice').textContent='Đã lập phiếu nháp'+(id?' ID '+id:'')+'. Tồn kho chưa thay đổi.';$('createReceipt').focus();
    }catch(error){if(key===identity)message(error instanceof TypeError?'Chưa xác nhận được kết quả. Kiểm tra phiếu trước khi gửi lại.':error.message||'Không thực hiện được yêu cầu.',true,'createError');}
    finally{setWriting(false);}
  };
  function confirmAction(action){
    if(writing)return;
    if(action.type==='approve'&&!canApprove(action.row))return;
    pending={...action,identity};$('confirmError').textContent='';
    $('confirmTitle').textContent=action.type==='approve'?'Duyệt phiếu ID '+action.id:action.type==='complete'?'Hoàn tất phiếu chuyển ID '+action.id:'Hủy phiếu chuyển ID '+action.id;
    $('confirmText').textContent=action.type==='cancel'?'Chỉ phiếu nháp mới được hủy. Xác nhận hủy phiếu chuyển này?':'Thao tác này cập nhật tồn kho nếu phiếu hợp lệ. Hãy đối chiếu ID và người lập trước khi xác nhận.';
    $('confirmReceipt').showModal();$('dismissConfirm').focus();
  }
  $('transferActionForm').onsubmit=event=>{
    event.preventDefault();if(writing||kind!=='transfers'||user?.role!=='WAREHOUSE_MANAGER')return;
    const id=$('transferActionId').value;if(!positive(id)){message('Nhập ID phiếu chuyển hợp lệ.',true,'transferMessage');return;}
    const type=event.submitter?.dataset.action;if(!['complete','cancel'].includes(type))return;
    if(type==='complete'&&lastTransfer?.id===Number(id)&&lastTransfer.createdBy===user.username){message('Người lập không được tự hoàn tất phiếu của mình. Cần quản lý kho khác xử lý.',true,'transferMessage');return;}
    confirmAction({type,id:Number(id),kind:'transfers'});
  };
  $('submitConfirm').onclick=async()=>{
    const action=pending;if(writing||!action||action.identity!==identity)return;
    const url=action.type==='approve'?configs[action.kind].write+'/'+action.id+'/approve':'/api/transfers/'+action.id+'/'+action.type;
    setWriting(true);message('',false,'confirmError');
    try{
      const response=await AppSession.write(url);if(!response.ok)throw new Error(await errorMessage(response));
      const row=await response.json();if(action.identity!==identity)return;
      $('confirmReceipt').close();pending=null;
      if(action.kind==='transfers'){transferResult(row);message('Đã '+(action.type==='cancel'?'hủy':'hoàn tất')+' phiếu ID '+action.id+'.',false,'transferMessage');}
      else{await load();$('writeNotice').textContent='Đã duyệt phiếu ID '+action.id+'.';}
    }catch(error){if(action.identity===identity)message(error instanceof TypeError?'Chưa xác nhận được kết quả. Đối chiếu phiếu trước khi thử lại.':error.message||'Không thực hiện được yêu cầu.',true,'confirmError');}
    finally{setWriting(false);}
  };
  $('dismissCreate').onclick=()=>{if(!writing)$('createDialog').close();};
  $('dismissConfirm').onclick=()=>{if(!writing){$('confirmReceipt').close();pending=null;}};
  for(const id of ['createDialog','confirmReceipt'])$(id).addEventListener('cancel',event=>{if(writing)event.preventDefault();});
  $('receiptSearch').onsubmit=event=>{event.preventDefault();const value=$('receiptLookup').value.trim();if(value&&!positive(value)){message('ID phải là số nguyên dương.',true);$('receiptLookup').focus();return;}lookup=value;page=0;load();};
  $('receiptReset').onclick=()=>{lookup='';page=0;$('receiptLookup').value='';load();};
  $('receiptSize').onchange=()=>{page=0;load();};
  $('viewMode').onchange=()=>{mode=$('viewMode').value;page=0;lookup='';$('receiptLookup').value='';$('lookupLabel').textContent=mode==='lines'?'Tra cứu theo ID dòng hàng':'Tra cứu theo ID phiếu';load();};
  $('receiptPrev').onclick=()=>{if(!loading&&page>0){--page;load();}};$('receiptNext').onclick=()=>{if(!loading){++page;load();}};
  document.querySelectorAll('[data-kind]').forEach(button=>{
    button.onclick=()=>switchKind(button.dataset.kind);
    button.onkeydown=event=>{const tabs=[...document.querySelectorAll('[data-kind]')].filter(node=>!node.hidden),index=tabs.indexOf(button);let next;if(event.key==='ArrowRight')next=(index+1)%tabs.length;else if(event.key==='ArrowLeft')next=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;event.preventDefault();tabs[next].focus();tabs[next].click();};
  });
  function session({account,status}){
    user=account;const key=user?user.username+':'+user.role:null;
    $('receiptGate').hidden=!!user;$('receiptContent').hidden=!AppSession.can('receipts',user);
    $('receiptSessionError').textContent=status==='unavailable'?'Chưa kiểm tra được phiên. Hãy tải lại trang.':user&&!AppSession.can('receipts',user)?'Tài khoản chưa được cấp quyền xem phiếu kho.':'';
    $('tab-transfers').hidden=user?.role!=='WAREHOUSE_MANAGER';
    if(key===identity)return;
    identity=key;++sequence;clear();lastTransfer=null;pending=null;$('lastTransfer').hidden=true;$('lastTransferData').replaceChildren();$('detailData').replaceChildren();$('createForm').reset();$('transferActionForm').reset();$('writeNotice').textContent='';message('',false,'transferMessage');
    for(const id of ['createDialog','receiptDetail','confirmReceipt','draftDialog'])if($(id).open)$(id).close();
    if(user)switchKind(kind==='transfers'&&user.role!=='WAREHOUSE_MANAGER'?'imports':kind,true);
  }
  let received=false;document.addEventListener('session:updated',event=>{received=true;session(event.detail);});
  AppSessionReady.then(account=>{if(!received)session({account,status:'ready'});});
})();
