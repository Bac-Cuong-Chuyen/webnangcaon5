(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  let register = false, pending = false;
  const next = new URLSearchParams(location.search).get('next');
  const message = (text, field) => {
    $('authError').textContent = text;
    if (field) { $(field).setAttribute('aria-invalid','true'); $(field).focus(); }
  };
  function mode(signUp) {
    if (pending) return;
    register = signUp;
    $('password').type='password';$('showPassword').textContent='Hiện mật khẩu';$('showPassword').setAttribute('aria-pressed','false');
    $('signInTab').setAttribute('aria-pressed',String(!register));
    $('signUpTab').setAttribute('aria-pressed',String(register));
    $('authTitle').textContent = register ? 'Tạo tài khoản' : 'Chào mừng trở lại';
    $('authDescription').textContent = register ? 'Đăng ký tài khoản nhân viên kho của bạn.' : 'Nhập tài khoản của bạn để tiếp tục.';
    $('confirmGroup').hidden = $('registrationNote').hidden = !register;
    $('confirmPassword').required = register;
    $('password').autocomplete = register ? 'new-password' : 'current-password';
    $('password').value = $('confirmPassword').value = '';
    $('submitAuth').textContent = register ? 'Tạo tài khoản' : 'Đăng nhập';
    $('authForm').querySelectorAll('[aria-invalid]').forEach(node => node.removeAttribute('aria-invalid'));
    message('');
  }
  $('signInTab').onclick = () => mode(false);
  $('signUpTab').onclick = () => mode(true);
  $('showPassword').onclick = () => {
    const show = $('password').type === 'password';
    $('password').type = show ? 'text' : 'password';
    $('showPassword').textContent = show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu';
    $('showPassword').setAttribute('aria-pressed',String(show));
  };
  $('authForm').addEventListener('input', event => { message(''); event.target.removeAttribute('aria-invalid'); });
  $('authForm').onsubmit = async event => {
    event.preventDefault(); if (pending) return;
    const username = $('username').value.trim(), password = $('password').value;
    if (!username) return message('Nhập tên đăng nhập.','username');
    if (register && password !== $('confirmPassword').value) return message('Hai mật khẩu chưa trùng nhau.','confirmPassword');
    pending = true;
    for (const id of ['submitAuth','signInTab','signUpTab']) $(id).disabled = true;
    $('authForm').setAttribute('aria-busy','true');
    $('submitAuth').textContent = register ? 'Đang tạo tài khoản…' : 'Đang đăng nhập…'; message('');
    try {
      const response = await AppSession.write(register ? '/api/auth/register' : '/api/auth/login', {username,password});
      if (!response.ok) {
        const text = response.status === 429 ? 'Đăng nhập tạm thời bị giới hạn. Vui lòng thử lại sau.' :
          response.status === 409 ? 'Tên đăng nhập đã được sử dụng. Hãy chọn tên khác.' :
          response.status === 401 ? 'Tên đăng nhập hoặc mật khẩu không đúng.' :
          response.status === 403 ? 'Yêu cầu chưa được xác nhận. Hãy tải lại trang và thử lại.' :
          'Không thực hiện được yêu cầu. Vui lòng kiểm tra thông tin và thử lại.';
        message(text, response.status === 409 || response.status === 401 ? 'username' : undefined);
        $('password').value = $('confirmPassword').value = ''; return;
      }
      $('password').value = $('confirmPassword').value = '';
      if (register) {
        pending = false; mode(false);
        $('sessionNotice').hidden = false;
        $('sessionNotice').textContent = 'Đã tạo tài khoản nhân viên kho. Bạn có thể đăng nhập ngay.';
        $('password').focus();
      } else {
        const user = await AppSession.refresh();
        if (!user) return message('Đã gửi đăng nhập nhưng chưa kiểm tra được phiên. Hãy thử lại.');
        location.assign(AppSession.destination(next,user));
      }
    } catch (_) { message('Không kết nối được máy chủ. Vui lòng thử lại.'); }
    finally {
      pending = false; $('authForm').setAttribute('aria-busy','false');
      for (const id of ['submitAuth','signInTab','signUpTab']) $(id).disabled = false;
      $('submitAuth').textContent = register ? 'Tạo tài khoản' : 'Đăng nhập';
    }
  };
  AppSessionReady.then(user => {
    if (!user) return;
    $('sessionNotice').hidden = false;
    $('sessionNotice').replaceChildren(document.createTextNode('Bạn đang đăng nhập với tài khoản '+user.username+'. '));
    const link = document.createElement('a'); link.href = AppSession.destination(next,user); link.textContent = 'Vào không gian làm việc';
    $('sessionNotice').appendChild(link);
  });
})();
