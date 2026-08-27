(()=>{
const root=document.documentElement;
const menuButton=document.querySelector('.menu-btn');
const nav=document.querySelector('.nav');
if(menuButton&&nav){menuButton.addEventListener('click',()=>{const open=nav.classList.toggle('open');menuButton.setAttribute('aria-expanded',String(open));});nav.querySelectorAll('a').forEach(a=>a.addEventListener('click',()=>{nav.classList.remove('open');menuButton.setAttribute('aria-expanded','false');}));}
const appearanceToggle=document.querySelector('.appearance-toggle');
let savedTheme='light';
try{savedTheme=localStorage.getItem('rss-site-theme')||'light';}catch(e){}
root.dataset.theme=savedTheme==='dark'?'dark':'light';
function updateAppearanceButton(){if(!appearanceToggle)return;const dark=root.dataset.theme==='dark';appearanceToggle.setAttribute('aria-label',dark?'Switch to light mode':'Switch to dark mode');appearanceToggle.title=dark?'Switch to light mode':'Switch to dark mode';const icon=appearanceToggle.querySelector('.appearance-icon');const label=appearanceToggle.querySelector('.appearance-label');if(icon)icon.textContent=dark?'☀':'☾';if(label)label.textContent=dark?'Light':'Dark';}
updateAppearanceButton();
if(appearanceToggle){appearanceToggle.addEventListener('click',()=>{root.dataset.theme=root.dataset.theme==='dark'?'light':'dark';try{localStorage.setItem('rss-site-theme',root.dataset.theme);}catch(e){}updateAppearanceButton();});}
const revealTargets=document.querySelectorAll('.section-heading,.project-card,.vision-card,.support-card,.project-hero,.visual-panel');
if('IntersectionObserver'in window){const observer=new IntersectionObserver((entries,obs)=>{entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add('is-visible');obs.unobserve(entry.target);}});},{threshold:.1,rootMargin:'0px 0px -35px 0px'});revealTargets.forEach((el,i)=>{el.style.setProperty('--reveal-delay',`${Math.min(i*55,220)}ms`);observer.observe(el);});}else revealTargets.forEach(el=>el.classList.add('is-visible'));
const year=document.getElementById('year');if(year)year.textContent=new Date().getFullYear();
})();