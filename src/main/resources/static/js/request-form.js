// 구매요청서 품목 줄: 줄 추가·삭제, 금액·합계 미리 보기.
// 화면 합계는 "미리 보기"일 뿐이고, 저장할 때 서버가 수량 × 단가를 다시 계산한다 (BR-07).
(function () {
  const tbody = document.getElementById('lines');
  const template = document.getElementById('line-template');
  const totalCell = document.getElementById('total');
  const MAX_LINES = 20;
  const won = (n) => Number(n || 0).toLocaleString('ko-KR');

  // Spring은 items[0], items[1] … 순서대로 리스트에 담는다.
  // 가운데 줄을 지우면 번호에 구멍이 생기므로(0, 2) 매번 0부터 다시 매긴다.
  function reindex() {
    tbody.querySelectorAll('tr.line').forEach((tr, i) => {
      tr.querySelectorAll('[name^="items["]').forEach((el) => {
        el.name = el.name.replace(/items\[\d+]/, `items[${i}]`);
        if (el.id) el.id = el.id.replace(/items\d+/, `items${i}`);
      });
    });
  }

  function recalc() {
    let total = 0;
    tbody.querySelectorAll('tr.line').forEach((tr) => {
      const qty = Number(tr.querySelector('.qty').value || 0);
      const price = Number(tr.querySelector('.price').value || 0);
      const amount = qty * price;
      tr.querySelector('.amount').textContent = won(amount);
      total += amount;
    });
    totalCell.textContent = won(total) + '원';
  }

  document.getElementById('add-line').addEventListener('click', () => {
    if (tbody.querySelectorAll('tr.line').length >= MAX_LINES) {
      alert('품목은 ' + MAX_LINES + '줄까지 입력할 수 있습니다.');
      return;
    }
    tbody.appendChild(template.content.cloneNode(true));
    reindex();
    recalc();
  });

  tbody.addEventListener('click', (e) => {
    if (!e.target.classList.contains('remove-line')) return;
    if (tbody.querySelectorAll('tr.line').length === 1) return; // 최소 한 줄
    e.target.closest('tr').remove();
    reindex();
    recalc();
  });

  // 품목을 고르면 단가가 비어 있을 때 기준 단가를 채워 준다
  tbody.addEventListener('change', (e) => {
    if (e.target.classList.contains('item')) {
      const opt = e.target.selectedOptions[0];
      const price = e.target.closest('tr').querySelector('.price');
      if (opt && opt.dataset.price && !price.value) price.value = Math.round(Number(opt.dataset.price));
      const qty = e.target.closest('tr').querySelector('.qty');
      if (!qty.value) qty.value = 1;
    }
    recalc();
  });
  tbody.addEventListener('input', recalc);

  document.getElementById('request-form').addEventListener('submit', reindex);
  recalc();
})();
