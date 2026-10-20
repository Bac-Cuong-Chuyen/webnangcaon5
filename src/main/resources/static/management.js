(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const configs={
    products:{title:'Danh sách sản phẩm',single:'sản phẩm',url:'/api/products',keys:['id','sku','name','unit','price'],headers:['ID','SKU','Tên sản phẩm','Đơn vị','Đơn giá'],fields:[['sku','SKU','text'],['name','Tên sản phẩm','text'],['unit','Đơn vị tính','text'],['price','Đơn giá','number']]},
    warehouses:{title:'Danh sách kho hàng',single:'kho hàng',url:'/api/warehouses',keys:['id','code','name','address'],headers:['ID','Mã kho','Tên kho','Địa chỉ'],fields:[['code','Mã kho','text'],['name','Tên kho','text'],['address','Địa chỉ','text']]},
    inventories:{title:'Số lượng tồn kho',single:'ngưỡng tồn kho',url:'/api/inventories',keys:['id','warehouseId','productId','quantity','minimumQuantity'],headers:['ID tồn','Kho','Sản phẩm','Số lượng','Ngưỡng tối thiểu'],fields:[['minimumQuantity','Ngưỡng tồn tối thiểu','number']]}
  };
  let tab='products',page=0,keyword='',user=null,identity=null,sequence=0,rows=[],editing=null,saving=false,readBusy=false;
  let filters={sortBy:'id',direction:'asc'};
  function resetFilters(){filters={sortBy:'id',direction:'asc'};for(const id of ['filterUnit','filterMin','filterMax'])$(id).value='';$('filterSort').value='id';$('filterDirection').value='asc';}
  function canEdit(){return tab==='inventories'?user?.role==='WAREHOUSE_MANAGER':['ADMIN','WAREHOUSE_MANAGER'].includes(user?.role);}
  function message(text,error=false){$('listMessage').textContent=text;$('listMessage').className=error?'error':'';}
  function clear(){rows=[];$('listRows').replaceChildren();$('listPage').textContent='Chưa có dữ liệu';$('previousPage').disabled=$('nextPage').disabled=true;}
  function updateButtons(){
    $('addRecord').hidden=!canEdit()||tab==='inventories';
    $('addRecord').textContent='Thêm '+configs[tab].single;
    $('listNote').textContent=tab==='inventories'?'Số lượng tồn thay đổi qua nghiệp vụ nhập, xuất và chuyển kho.':canEdit()?'Thêm và cập nhật thông tin '+configs[tab].single+'.':'Bạn có quyền tra cứu dữ liệu.';
  }
  function selectTab(next){
    tab=next;resetFilters();$('productFilters').hidden=tab!=='products';page=0;keyword='';$('searchInput').value='';clear();updateButtons();
    document.querySelectorAll('[data-tab]').forEach(button=>{const active=button.dataset.tab===tab;button.setAttribute('aria-selected',String(active));button.tabIndex=active?0:-1;});
    $('managementPanel').setAttribute('aria-labelledby','tab-'+tab);
    $('listTitle').textContent=$('listCaption').textContent=configs[tab].title;
    $('searchLabel').textContent=tab==='products'?'Tìm theo tên hoặc SKU':tab==='warehouses'?'Tra cứu theo ID kho':'Tra cứu theo ID tồn kho';
    $('searchInput').placeholder=tab==='products'?'Nhập từ khóa sản phẩm':'Nhập ID hoặc để trống để xem danh sách';
    load();
  }
  async function api(url,options={}){
    const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},...options});
    if(response.status===401){await AppSession.refresh();throw new Error('Phiên đã kết thúc. Vui lòng đăng nhập lại.');}
    if(response.status===403)throw new Error('Tài khoản hiện tại không có quyền thực hiện thao tác này.');
    if(!response.ok)throw new Error(response.status===404?'Không tìm thấy bản ghi.':'Không tải được dữ liệu. Vui lòng thử lại.');
    return response;
  }
  async function load(){
    const requestId=++sequence;
    if(!user||!AppSession.can('management',user)){clear();return;}
    const config=configs[tab],currentTab=tab,size=Number($('pageSize').value);
    clear();message('Đang tải dữ liệu…');readBusy=true;$('managementPanel').setAttribute('aria-busy','true');
    try{
      const params=new URLSearchParams({page:String(page),size:String(size)});
      let url=config.url+'?'+params,searching=currentTab==='products',byId=currentTab!=='products'&&!!keyword;
      if(searching){params.set('keyword',keyword);for(const [key,value] of Object.entries(filters))if(value!=='')params.set(key,value);url='/api/products/search?'+params;}
      if(byId)url=config.url+'/'+encodeURIComponent(keyword);
      const response=await api(url);const data=await response.json();
      if(requestId!==sequence)return;
      rows=searching?data.content:byId?[data]:data;
      if(!Array.isArray(rows))throw new Error('Dữ liệu phản hồi chưa hợp lệ. Hãy thử lại.');
      const hasNext=!byId&&(searching?!data.last:response.headers.get('X-Has-Next')==='true');
      draw();$('previousPage').disabled=page===0||byId;$('nextPage').disabled=!hasNext;
      $('listPage').textContent=byId?'Kết quả tra cứu ID '+keyword:'Trang '+(page+1)+' · '+rows.length+' bản ghi'+(searching?' / '+Number(data.totalElements).toLocaleString('vi-VN')+' kết quả':'');
      message(rows.length?'Đã tải '+rows.length+' bản ghi.':'Không có dữ liệu phù hợp.');
    }catch(error){if(requestId===sequence){clear();message(error.message||'Không kết nối được máy chủ.',true);}}
    finally{if(requestId===sequence){readBusy=false;$('managementPanel').setAttribute('aria-busy','false');}}
  }
  function draw(){
    const config=configs[tab],head=document.createElement('tr');
    for(const label of config.headers.concat(canEdit()?['Thao tác']:[])){const th=document.createElement('th');th.scope='col';th.textContent=label;head.append(th);}
    $('listHead').replaceChildren(head);
    const fragment=document.createDocumentFragment();
    for(const row of rows){
      const tr=document.createElement('tr');
      for(const key of config.keys){
        const td=document.createElement('td');
        td.textContent=row[key]===null||row[key]===undefined?'—':key==='price'?Number(row[key]).toLocaleString('vi-VN',{maximumFractionDigits:2}):String(row[key]);
        if(tab==='inventories'&&['warehouseId','productId'].includes(key))EntityLabels.mark(td,key==='warehouseId'?'warehouses':'products',row[key]);
        if(['quantity','minimumQuantity','price'].includes(key))td.className='number';tr.append(td);
      }
      if(canEdit()){
        const td=document.createElement('td'),button=document.createElement('button');button.type='button';button.className='ghost';button.textContent=tab==='inventories'?'Đặt ngưỡng':'Sửa';
        button.setAttribute('aria-label',(tab==='inventories'?'Đặt ngưỡng tồn ID ':'Sửa '+config.single+' ')+(row.sku||row.code||row.id));
        button.onclick=()=>openEditor(row);td.append(button);tr.append(td);
      }
      if(tab==='inventories'&&row.minimumQuantity!=null&&row.quantity<row.minimumQuantity){const badge=document.createElement('span');badge.className='stock-low';badge.textContent='Thấp hơn ngưỡng';tr.children[3].append(document.createElement('br'),badge);}
      fragment.append(tr);
    }
    $('listRows').replaceChildren(fragment);EntityLabels.hydrate($('listRows'));
  }
  function openEditor(row=null){
    if(!canEdit())return;
    editing=row;$('editorError').textContent='';$('editorFields').replaceChildren();
    $('editorTitle').textContent=tab==='inventories'?'Đặt ngưỡng tồn tối thiểu':(row?'Sửa ':'Thêm ')+configs[tab].single;
    $('editorNote').textContent=tab==='inventories'?'Tồn ID '+row.id+' · Kho ID '+row.warehouseId+' · Sản phẩm ID '+row.productId:'Các trường bên dưới là bắt buộc.';
    for(const [key,label,type] of configs[tab].fields){
      const element=document.createElement('label');element.htmlFor='edit-'+key;element.textContent=label;
      const input=document.createElement('input');input.id='edit-'+key;input.name=key;input.type=type;input.required=true;input.setAttribute('aria-describedby','editorError');
      if(type==='number'){input.min='0';input.step=key==='price'?'0.01':'1';input.max=key==='price'?'999999999999.99':'2147483647';}
      else input.maxLength=key==='address'?255:120;
      input.value=row?.[key]??(type==='number'?'0':'');$('editorFields').append(element,input);
    }
    $('editorDialog').showModal();$('editorFields').querySelector('input').focus();
  }
  $('editorForm').onsubmit=async event=>{
    event.preventDefault();if(saving||!canEdit())return;
    const savedIdentity=identity,savedTab=tab,config=configs[tab],payload={};
    for(const [key,,type] of config.fields){
      const input=$('edit-'+key),value=input.value.trim();
      if(!value){$('editorError').textContent='Vui lòng nhập đầy đủ thông tin.';input.focus();return;}
      payload[key]=type==='number'?Number(value):value;
      if(type==='number'&&(!Number.isFinite(payload[key])||payload[key]<0||key!=='price'&&!Number.isInteger(payload[key]))){$('editorError').textContent='Giá trị số phải hợp lệ và không âm.';input.focus();return;}
    }
    saving=true;$('editorError').textContent='';$('saveRecord').disabled=$('cancelEdit').disabled=true;$('editorForm').setAttribute('aria-busy','true');
    try{
      const response=await AppSession.write(config.url+(editing?'/'+editing.id:''),payload,editing?'PUT':'POST');
      if(!response.ok){
        if(response.status===401)await AppSession.refresh();
        throw new Error(response.status===403?'Tài khoản không có quyền lưu thay đổi.':response.status===409?'Dữ liệu đang xung đột. Hãy kiểm tra mã và tải lại danh sách.':'Chưa lưu được thay đổi. Hãy kiểm tra thông tin và thử lại.');
      }
      if(savedIdentity!==identity||savedTab!==tab)return;
      $('editorDialog').close();await load();message('Đã lưu thông tin '+config.single+'.');$('addRecord').hidden?$('tab-'+tab).focus():$('addRecord').focus();
    }catch(error){if(savedIdentity===identity)$('editorError').textContent=error.message||'Không kết nối được máy chủ.';}
    finally{saving=false;$('saveRecord').disabled=$('cancelEdit').disabled=false;$('editorForm').setAttribute('aria-busy','false');}
  };
  $('editorDialog').addEventListener('cancel',event=>{if(saving)event.preventDefault();});
  $('cancelEdit').onclick=()=>{if(!saving)$('editorDialog').close();};
  $('addRecord').onclick=()=>openEditor();
  $('searchForm').onsubmit=event=>{
    event.preventDefault();
    if(tab==='products'){
      const min=$('filterMin').value,max=$('filterMax').value;
      if((min!==''&&(!Number.isFinite(Number(min))||Number(min)<0))||(max!==''&&(!Number.isFinite(Number(max))||Number(max)<0))||(min!==''&&max!==''&&Number(min)>Number(max))){message('Khoảng giá không hợp lệ: giá từ phải nhỏ hơn hoặc bằng giá đến.',true);$('filterMin').focus();return;}
      filters={unit:$('filterUnit').value.trim(),minPrice:min,maxPrice:max,sortBy:$('filterSort').value,direction:$('filterDirection').value};
    }
    keyword=$('searchInput').value.trim();
    if(tab!=='products'&&keyword&&!/^[1-9]\d*$/.test(keyword)){message('ID phải là số nguyên dương.',true);$('searchInput').focus();return;}
    page=0;load();
  };
  $('resetSearch').onclick=()=>{resetFilters();keyword='';$('searchInput').value='';page=0;load();};
  $('pageSize').onchange=()=>{page=0;load();};
  $('previousPage').onclick=()=>{if(page>0&&!readBusy){--page;load();}};
  $('nextPage').onclick=()=>{if(!readBusy){++page;load();}};
  const tabs=Object.keys(configs);
  document.querySelectorAll('[data-tab]').forEach((button,index)=>{
    button.onclick=()=>{if(!saving)selectTab(button.dataset.tab);};
    button.onkeydown=event=>{let next;if(event.key==='ArrowRight')next=(index+1)%tabs.length;else if(event.key==='ArrowLeft')next=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;event.preventDefault();$('tab-'+tabs[next]).focus();$('tab-'+tabs[next]).click();};
  });
  function session({account,status}){
    const key=account?account.username+':'+account.role:null;
    $('guestGate').hidden=!!account;$('managementContent').hidden=!AppSession.can('management',account);
    $('sessionError').textContent=status==='unavailable'?'Chưa kiểm tra được phiên. Hãy tải lại trang.':account&&!AppSession.can('management',account)?'Tài khoản chưa được cấp quyền quản lý kho.':'';
    user=account;
    if(identity===key)return;
    identity=key;++sequence;readBusy=false;clear();if($('editorDialog').open)$('editorDialog').close();
    updateButtons();if(user)selectTab(tab);
  }
  let received=false;
  document.addEventListener('session:updated',event=>{received=true;session(event.detail);});
  AppSessionReady.then(account=>{if(!received)session({account,status:'ready'});});
})();
