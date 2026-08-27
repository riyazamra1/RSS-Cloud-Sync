const menuButton=document.querySelector('.menu-btn');
const nav=document.querySelector('.nav');
if(menuButton&&nav){menuButton.addEventListener('click',()=>{const open=nav.classList.toggle('open');menuButton.setAttribute('aria-expanded',String(open));});document.querySelectorAll('.nav a').forEach(a=>a.addEventListener('click',()=>{nav.classList.remove('open');menuButton.setAttribute('aria-expanded','false');}));}
const appearanceToggle=document.querySelector('.appearance-toggle');
const savedTheme=localStorage.getItem('rss-site-theme')||'light';
document.documentElement.dataset.theme=savedTheme;
function updateAppearanceButton(){if(!appearanceToggle)return;const dark=document.documentElement.dataset.theme==='dark';appearanceToggle.setAttribute('aria-label',dark?'Switch to light mode':'Switch to dark mode');appearanceToggle.querySelector('.appearance-icon').textContent=dark?'☀':'☾';appearanceToggle.querySelector('.appearance-label').textContent=dark?'Light':'Dark';}
updateAppearanceButton();
if(appearanceToggle){appearanceToggle.addEventListener('click',()=>{const next=document.documentElement.dataset.theme==='dark'?'light':'dark';document.documentElement.dataset.theme=next;localStorage.setItem('rss-site-theme',next);updateAppearanceButton();});}
const revealTargets=document.querySelectorAll('.section-heading,.project-card,.vision-card,.support-card,.grid article');
if('IntersectionObserver' in window){const observer=new IntersectionObserver((entries,obs)=>{entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add('is-visible');obs.unobserve(entry.target);}});},{threshold:.12,rootMargin:'0px 0px -30px 0px'});revealTargets.forEach(el=>observer.observe(el));}else{revealTargets.forEach(el=>el.classList.add('is-visible'));}
const year=document.getElementById('year');if(year)year.textContent=new Date().getFullYear();