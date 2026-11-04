/* A local visual study. No requests, account writes, storage, or app integration. */
const icon = name => `<svg class="icon" aria-hidden="true"><use href="#${name}"></use></svg>`;
const frames = [
  {image:'sea.jpg', title:'Море в последний час перед закатом', channel:'Тихие места', stats:'18 тыс. просмотров · 2 дня назад', duration:'18:42'},
  {image:'architecture.jpg', title:'Как свет меняет архитектуру города', channel:'Форма и свет', stats:'42 тыс. просмотров · 5 дней назад', duration:'24:16'},
  {image:'forest.jpg', title:'Утро у озера: остаться в тишине', channel:'Вне города', stats:'9,6 тыс. просмотров · 1 день назад', duration:'32:08'},
  {image:'sea.jpg', title:'Просто слушать воду и никуда не спешить', channel:'Тихие места', stats:'26 тыс. просмотров · 1 неделю назад', duration:'45:00'}
];
const concepts = {
  film:{title:'Тёплая экспозиция', summary:'Свет проходит через воздух, а не через кнопки. Плёночное зерно, сливочные света и оливковые тени; мягкая дымка связывает интерфейс с кадром.',
    light:['#F4F0E8','#FFF9EF','#292E26','#616757','#984C34'],dark:['#191C18','#282C25','#F3F0E7','#B8BDAE','#E9A786'],
    type:'Lora 26 — только заголовок экрана. Golos Text 16/22 — названия, 12/16 — метаданные. Плотность ленты сохраняется; выразительный шрифт не мешает быстро читать список.',
    material:'Неподвижный скан 35-мм зерна на фоне. Почти матовый док с лёгким рассеянием света. Под текстом сохраняется плотная спокойная подложка; сами превью не тонируются.',
    movement:'Смена светового поля за 1 100 мс, ease-in-out. Во время перехода фон теряет детали сильнее, затем возвращается к мягкому расфокусу. У кнопок нет постоянного свечения.',
    verdict:'Рекомендую. Ближе всего к натуральности трейлеров Claude и достаточно спокойно для ежедневной ленты. Риск: крупное зерно даст ощущение пыли; здесь оно мелкое и неподвижное.'},
  opal:{title:'Опаловая вода', summary:'Отражённый закат в воде. Цвет кадра проходит через молочное стекло дока; контент остаётся резким, а прозрачность объясняет, какой слой находится ближе к пальцу.',
    light:['#F1F4EC','#F8FBF4','#24332D','#53685C','#7C442D'],dark:['#101F1B','#213A31','#EDF4E8','#B3C8B7','#EFC39A'],
    type:'Onest во всей системе: 26/32 — заголовок, 16/22 — видео, 12/16 — метаданные. Более округлые очертания поддерживают мягкий материал; цифры времени не скачут по ширине.',
    material:'Молочное стекло — только в доке, полосе фильтров и временном меню. Читаемость защищена 90–94% плотностью материала. Один светлый край отделяет управление; никаких прозрачных карточек.',
    movement:'Тот же свет из изображения, переход за 1 100 мс. Материал дока неподвижен; свет в нём меняется вместе с фоном. В приложении отклик касания — короткий световой сдвиг, без волн и пружин.',
    verdict:'Самое явное ощущение парения. Риски выше: стекло может потеряться на пёстром видео, а размытие нескольких слоёв увеличит нагрузку. В темноте потребуются строгие ограничения яркости.'},
  porcelain:{title:'Светлый фарфор', summary:'Тёплый свет падает на матовую поверхность. У интерфейса собственный спокойный цвет, а видео лишь слегка окрашивает воздух вокруг него. Парение создаёт мягкая контактная тень.',
    light:['#F6F2EA','#FFFCF6','#2B2D26','#626553','#49613C'],dark:['#22221F','#33332D','#F1F1E5','#B6BAA4','#C3CF9A'],
    type:'Roboto Flex: 26/32 — заголовок, 16/22 — названия, 12/16 — метаданные. Привычная Android-типографика с оптической настройкой размеров. Характер дают пропорции и тон материала.',
    material:'Непрозрачный матовый док цвета слоновой кости или тёплого графита. Слабый свет кадра виден вокруг контента. Тонкое реальное зерно едва ощущается; зеркальных краёв и преломления нет.',
    movement:'Цвет кадра меняется за 1 100 мс, но с меньшей амплитудой. Меню проявляется коротко и спокойно. Фон не двигается, когда прокрутка и воспроизведение стоят.',
    verdict:'Наиболее предсказуемая читаемость и самая простая экономная реализация. Риск: heavenly выражено слабее; при чрезмерном упрощении останется обычная бежевая тема.'}
};
const dockItems = [['library','Моё'],['search','Поиск'],['shorts','Shorts'],['subscriptions','Подписки'],['home','Главная']];
function phone(theme) {
  return `<section class="phone-set"><div class="theme-label"><span>${theme==='light'?'Светлая тема':'Тёмная тема'}</span><span>390 × 844 · 1×</span></div>
  <div class="phone" data-theme="${theme}" aria-label="Лента, ${theme==='light'?'светлая':'тёмная'} тема">
    <div class="ambient" aria-hidden="true"><div class="wash on" style="background-image:url('assets/sea.jpg')"></div><div class="wash"></div></div><div class="neutral"></div><div class="grain"></div>
    <div class="status" aria-hidden="true"><span>9:41</span><span class="status-symbols"><svg viewBox="0 0 12 12"><path d="M1 10V8h2v2H1Zm3 0V6h2v4H4Zm3 0V4h2v6H7Zm3 0V2h2v8h-2Z"/></svg><svg viewBox="0 0 12 12"><path d="M0 4a10 10 0 0 1 12 0l-6 7-6-7Z"/></svg><i class="battery"></i></span></div>
    <div class="feed-scroll" tabindex="0" aria-label="Прокручиваемая лента">
      <div class="screen-header"><h3>Главная</h3><p>Все</p></div>
      ${frames.map((f,i)=>`<article class="video-card" data-frame="${i}"><div class="thumb"><img src="assets/${f.image}" alt="${f.title}" draggable="false"><span class="duration">${f.duration}</span>${i===0?'<div class="watched"></div>':''}</div><div class="video-meta"><div class="avatar" aria-hidden="true"><img src="assets/${f.image}" alt=""></div><div class="video-copy"><h4 class="video-title">${f.title}</h4><p class="video-stats">${f.channel}<br>${f.stats}</p></div><button class="more-button" aria-label="Меню видео: ${f.title}">${icon('more')}</button></div></article>`).join('')}
    </div>
    <div class="filters" aria-label="Категории ленты"><button class="filter">Музыка</button><button class="filter">Кино</button><button class="filter">Подкасты</button><button class="filter active" aria-pressed="true">Все</button></div>
    <nav class="dock" aria-label="Разделы ErgoTube">${dockItems.map(([id,label])=>`<button class="${id==='home'?'active':''}" ${id==='home'?'aria-current="page"':''} aria-label="${label}">${icon(id)}<span>${label}</span></button>`).join('')}<div class="gesture" aria-hidden="true"></div></nav>
    <div class="reach-line" aria-hidden="true"><span>55% высоты</span></div>
    <div class="fan-overlay" aria-label="Внешний вид меню удержания"><div class="fan-hint">Проведите к действию</div><div class="fan-ring"></div>
      <button class="fan-item up">${icon('schedule')}<span>Посмотреть позже</span></button><button class="fan-item right">${icon('download')}<span>Скачать</span></button><button class="fan-item left">${icon('block')}<span>Не интересует</span></button><button class="fan-item down">${icon('share')}<span>Поделиться</span></button><button class="fan-center" aria-label="Закрыть меню">${icon('shorts')}</button>
    </div>
  </div></section>`;
}
document.querySelector('#phones').innerHTML=phone('light')+phone('dark');
const phones=[...document.querySelectorAll('.phone')];
const scrolls=phones.map(p=>p.querySelector('.feed-scroll'));
const sizeObserver=new ResizeObserver(entries=>entries.forEach(({target,contentRect})=>{
  target.closest('.phone-set').querySelector('.theme-label span:last-child').textContent=`${Math.round(contentRect.width)} × ${Math.round(contentRect.height)} · 1×`;
}));
phones.forEach(p=>sizeObserver.observe(p));
let currentDirection='film', currentFrame=0, lastUserScroll=0, syncLock=false, ambientTimer;
let selectedImage='sea.jpg';
function renderNotes() {
  const c=concepts[currentDirection];
  document.querySelector('#concept-title').textContent=c.title;
  document.querySelector('#concept-summary').textContent=c.summary;
  document.querySelector('#material-notes').innerHTML=`<div><h3>Светлая / тёмная палитра</h3>${[c.light,c.dark].map(p=>`<div class="swatches">${p.map(hex=>`<span class="swatch" style="background:${hex}"></span>`).join('')}</div><div class="palette-values">${p.join(' · ')}</div>`).join('')}<p>Фон · поверхность · текст · метаданные · акцент</p></div><div><h3>Шрифт</h3><p>${c.type}</p></div><div><h3>Материал</h3><p>${c.material}</p></div><div><h3>Движение</h3><p>${c.movement}</p></div><div style="grid-column:1/-1"><h3>Мой вывод</h3><p>${c.verdict}</p></div>`;
}
function setDirection(id) {
  currentDirection=id;document.body.dataset.direction=id;
  document.querySelectorAll('[data-pick]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.pick===id)));
  renderNotes();
}
function setAmbient(index) {
  currentFrame=index;const image=frames[index].image;if(image===selectedImage)return;selectedImage=image;
  for(const p of phones){
    const layers=[...p.querySelectorAll('.wash')],active=layers.find(l=>l.classList.contains('on')),next=layers.find(l=>l!==active);
    const quiet=document.body.classList.contains('no-motion')||matchMedia('(prefers-reduced-motion: reduce)').matches;
    next.style.backgroundImage=`url('assets/${image}')`;p.classList.toggle('shifting',!quiet);next.classList.add('on');active.classList.remove('on');
    clearTimeout(p.blurTimer);if(!quiet)p.blurTimer=setTimeout(()=>p.classList.remove('shifting'),600);
  }
}
function frameForScroll(scroller) {
  const view=scroller.getBoundingClientRect(),center=(view.top+view.bottom)/2;
  let index=0,best=-Infinity;
  scroller.querySelectorAll('.thumb').forEach((thumb,i)=>{
    const r=thumb.getBoundingClientRect(),visible=Math.max(0,Math.min(r.bottom,view.bottom)-Math.max(r.top,view.top));
    const score=visible-Math.abs((r.top+r.bottom)/2-center)*.05;
    if(score>best){best=score;index=i;}
  });
  setAmbient(index);
}
scrolls.forEach((s,i)=>s.addEventListener('scroll',()=>{
  if(syncLock)return;syncLock=true;
  const other=scrolls[1-i];other.scrollTop=s.scrollTop;
  const downward=s.scrollTop>lastUserScroll+2;const upward=s.scrollTop<lastUserScroll-2;
  if(downward||upward||s.scrollTop<12)phones.forEach(p=>p.classList.toggle('reading',downward&&s.scrollTop>40));
  lastUserScroll=s.scrollTop;clearTimeout(ambientTimer);ambientTimer=setTimeout(()=>frameForScroll(s),120);requestAnimationFrame(()=>syncLock=false);
},{passive:true}));
document.querySelectorAll('[data-pick]').forEach(b=>b.addEventListener('click',()=>setDirection(b.dataset.pick)));
document.querySelector('#next-frame').addEventListener('click',()=>{
  const index=(currentFrame+1)%frames.length;const card=scrolls[0].querySelector(`[data-frame="${index}"]`);
  scrolls[0].scrollTo({top:Math.max(0,card.offsetTop-70),behavior:document.body.classList.contains('no-motion')||matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});
});
function setFan(open){document.querySelectorAll('.fan-overlay').forEach(el=>el.classList.toggle('open',open));document.querySelector('#show-fan').setAttribute('aria-pressed',String(open));}
document.querySelector('#show-fan').addEventListener('click',()=>setFan(document.querySelector('#show-fan').getAttribute('aria-pressed')!=='true'));
document.querySelectorAll('.more-button').forEach(b=>b.addEventListener('click',()=>setFan(true)));
document.querySelectorAll('.fan-overlay').forEach(f=>f.addEventListener('click',e=>{if(e.target===f||e.target.closest('.fan-center'))setFan(false);}));
document.addEventListener('keydown',e=>{if(e.key==='Escape')setFan(false)});
document.querySelector('#left-hand').addEventListener('change',e=>document.body.classList.toggle('left-handed',e.target.checked));
document.querySelector('#show-reach').addEventListener('change',e=>document.body.classList.toggle('show-reach',e.target.checked));
const reduce=matchMedia('(prefers-reduced-motion: reduce)');document.querySelector('#quiet-motion').checked=reduce.matches;
document.body.classList.toggle('no-motion',reduce.matches);
document.querySelector('#quiet-motion').addEventListener('change',e=>{document.body.classList.toggle('no-motion',e.target.checked);if(e.target.checked)phones.forEach(p=>{clearTimeout(p.blurTimer);p.classList.remove('shifting');});});
/* Local-only controls show selection, without pretending to navigate or filter actual YouTube. */
document.querySelectorAll('.filter').forEach(b=>b.addEventListener('click',()=>{for(const p of phones){p.querySelectorAll('.filter').forEach(x=>{const selected=x.textContent===b.textContent;x.classList.toggle('active',selected);x.setAttribute('aria-pressed',String(selected));});p.querySelector('.screen-header p').textContent=b.textContent;}}));
document.querySelectorAll('.dock button').forEach(b=>b.addEventListener('click',()=>{for(const p of phones){p.querySelectorAll('.dock button').forEach(x=>{const selected=x.getAttribute('aria-label')===b.getAttribute('aria-label');x.classList.toggle('active',selected);if(selected)x.setAttribute('aria-current','page');else x.removeAttribute('aria-current');});}}));
renderNotes();
