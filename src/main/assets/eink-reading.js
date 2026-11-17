// 阅读图片完整落页，并用文末留白保留末页的正常起点。
(() => {
    if (window.HydrogenReadingLayout) return;
    let allowed = false, queued = false, started = false, overlapLines = 2, waiting = null;
    let anchor = null, viewportKey = '', revision = 0;
    const observed = new WeakSet();
    const blocks = new Set();
    const baseMargins = new WeakMap();
    const root = () => document.querySelector('.RichText, .ztext, .content-wrap') || document.body;
    const editable = () => {
        const e = document.activeElement;
        return e && (e.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName));
    };
    function metrics() {
        const visual = window.visualViewport;
        const height = Math.max(1, Math.min(innerHeight, visual ? visual.height : innerHeight));
        let line = 24;
        const content = root();
        if (content) for (const e of [content, ...content.querySelectorAll('p, li, pre, h1, h2, h3, blockquote')]) {
            const r = e.getBoundingClientRect();
            if (r.bottom <= 0 || r.top >= height) continue;
            const css = getComputedStyle(e);
            line = Math.max(line, parseFloat(css.lineHeight) || (parseFloat(css.fontSize) || 16) * 1.5);
        }
        const overlap = Math.min(height - 1, Math.ceil(line * Math.max(1, Math.min(5, overlapLines))));
        return { height, overlap, step:Math.max(1, Math.floor(height - overlap)), revision };
    }
    function capture() {
        const content = root();
        if (!content) return;
        const height = Math.min(innerHeight, window.visualViewport ? window.visualViewport.height : innerHeight);
        const r = document.caretRangeFromPoint && document.caretRangeFromPoint(16, Math.min(24, height - 1));
        if (r && r.startContainer.nodeType === 3 && content.contains(r.startContainer) && r.getBoundingClientRect().height > 0) {
            anchor = { range:r, top:r.getBoundingClientRect().top, scroll:scrollY };
        } else {
            const node = [...content.querySelectorAll('p, img, li, pre')].find(e => e.getBoundingClientRect().bottom > 0);
            anchor = node ? { node, top:node.getBoundingClientRect().top, scroll:scrollY } : null;
        }
    }
    function restore() {
        if (!anchor || anchor.scroll <= 0.5) return;
        const node = anchor.node || anchor.range.startContainer;
        if (!node.isConnected) return;
        const r = anchor.node ? node.getBoundingClientRect() : anchor.range.getBoundingClientRect();
        const delta = r.top - anchor.top;
        if (Math.abs(delta) > 0.5) window.scrollBy({top:delta, behavior:'instant'});
    }
    function tail() {
        let e = document.getElementById('hydrogen-page-tail');
        if (!e) {
            e = document.createElement('div');
            e.id = 'hydrogen-page-tail';
            e.setAttribute('aria-hidden', 'true');
        }
        if (document.body.lastElementChild !== e) document.body.appendChild(e);
        return e;
    }
    function isPicture(img) {
        const r = img.getBoundingClientRect();
        return r.height >= 32 && r.width > 0 && !img.closest('header, nav, .Avatar, .avatar')
            && !/equation/.test(img.src) && getComputedStyle(img).position !== 'fixed';
    }
    function pictures() {
        const content = root();
        if (!content) return [];
        const m = metrics();
        return [...content.querySelectorAll('img')].filter(isPicture).map(img => {
            let block = img;
            let figure = img.closest('figure');
            while (figure && content.contains(figure) && figure.querySelectorAll('img').length === 1) {
                if (figure.getBoundingClientRect().height <= m.height - 2) block = figure;
                figure = figure.parentElement && figure.parentElement.closest('figure');
            }
            if (block === img) {
                const paragraph = img.closest('p');
                if (paragraph && paragraph.querySelectorAll('img').length === 1 && paragraph.getBoundingClientRect().height <= m.height - 2) block = paragraph;
            }
            return block;
        });
    }
    function gap(block, amount) {
        if (!baseMargins.has(block)) baseMargins.set(block, getComputedStyle(block).marginTop);
        block.setAttribute('data-hydrogen-image-block', '');
        block.style.setProperty('--hydrogen-base-margin', baseMargins.get(block));
        const current = parseFloat(block.style.getPropertyValue('--hydrogen-page-gap')) || 0;
        block.style.setProperty('--hydrogen-page-gap', `${current + amount}px`);
        blocks.add(block);
    }
    function prepare(start) {
        const m = metrics();
        const pending = [];
        for (const block of pictures()) {
            let r = block.getBoundingClientRect();
            const top = r.top + scrollY;
            const bottom = r.bottom + scrollY;
            if (top >= start - 0.5 && top < start + m.height && bottom > start + m.height + 0.5) {
                gap(block, Math.ceil(start + m.height - top) + 1);
                r = block.getBoundingClientRect();
                pending.push({ top:r.top + scrollY, bottom:r.bottom + scrollY, height:r.height });
            } else if (top >= start + m.height - 0.5 && top <= start + m.height + 2) {
                pending.push({top, bottom, height:r.height});
            }
        }
        return { ...m, pending };
    }
    function contentEnd() { return tail().getBoundingClientRect().top + scrollY; }
    function configure() {
        if (!document.body || editable()) return;
        const m = metrics();
        const key = `${innerWidth}:${m.height}:${overlapLines}`;
        if (key !== viewportKey) {
            viewportKey = key;
            revision++;
            for (const block of blocks) if (block.isConnected) block.style.setProperty('--hydrogen-page-gap','0px');
        }
        document.documentElement.style.setProperty('--hydrogen-image-height', `${Math.max(24, m.height - 32)}px`);
        const end = tail();
        const spacer = contentEnd() > m.height ? Math.max(innerHeight, m.height) : 0;
        if (end.style.height !== `${spacer}px`) end.style.height = `${spacer}px`;
        for (const img of root().querySelectorAll('img')) {
            if (isPicture(img)) img.setAttribute('data-hydrogen-reading-image', '');
            if (observed.has(img)) continue;
            observed.add(img);
            img.addEventListener('load', () => { revision++; schedule(); });
            if (resize) resize.observe(img);
        }
        restore();
        prepare(scrollY);
        capture();
    }
    function schedule() {
        if (queued || !allowed) return;
        queued = true;
        requestAnimationFrame(() => { configure(); queued = false; });
    }
    const resize = window.ResizeObserver ? new ResizeObserver(schedule) : null;
    function init(force = false, lines = 2) {
        overlapLines = lines;
        allowed = allowed || force || !!document.querySelector('.RichText, .ztext, .content-wrap');
        if (!document.body) return;
        if (!allowed) {
            if (!waiting) {
                waiting = new MutationObserver(() => {
                    if (document.querySelector('.RichText, .ztext, .content-wrap')) init(false,overlapLines);
                });
                waiting.observe(document.body,{childList:true,subtree:true});
            }
            return;
        }
        if (waiting) { waiting.disconnect();waiting = null; }
        if (!started) {
            started = true;
            const style = document.createElement('style');
            style.textContent = `
                html,body {overflow-anchor:none;}
                .RichText img,.ztext img,.content-wrap img,[data-hydrogen-reading-image] {max-width:100%!important;height:auto!important;width:auto!important;max-height:var(--hydrogen-image-height)!important;object-fit:contain;}
                [data-hydrogen-image-block] {margin-top:calc(var(--hydrogen-base-margin,0px) + var(--hydrogen-page-gap,0px))!important;}
                #hydrogen-page-tail {display:block;clear:both;width:1px;margin:0!important;padding:0!important;border:0!important;pointer-events:none;flex-shrink:0;}
            `;
            document.head.appendChild(style);
            new MutationObserver(schedule).observe(document.body,{childList:true,subtree:true});
            window.addEventListener('scroll', capture,{passive:true});
            window.addEventListener('resize',schedule);
            if (window.visualViewport) window.visualViewport.addEventListener('resize',schedule);
        }
        capture();
        configure();
    }
    window.HydrogenReadingLayout = {init,metrics,prepare,contentEnd,pictures,capture,revision:()=>revision};
})();

// 虚拟按钮与实体键共用滚动起点，图片整体落页，末页保持留白。
(() => {
    if (window.HydrogenReadingPager) return;
    let context = null, history = [], index = 0, expected = 0, layoutRevision = -1;
    window.HydrogenReadingPager = {
        turn(direction, overlapLines = 2) {
            const active = document.activeElement;
            if (active && (active.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))) return scrollY;
            const layout = window.HydrogenReadingLayout;
            layout.init(true,overlapLines);
            const content = document.querySelector('.RichText, .ztext, .content-wrap') || document.body;
            let current = scrollY;
            const rev = layout.revision();
            if (content !== context || Math.abs(current - expected) > 2 || rev !== layoutRevision) {
                context = content; history = [current]; index = 0;
            }
            const page = layout.prepare(current);
            const end = layout.contentEnd();
            let target;
            if (direction > 0) {
                if (current + page.height >= end - 0.5) { expected = current; layoutRevision = rev; return current; }
                target = index + 1 < history.length ? history[index + 1] : current + page.step;
                const large = page.pending.find(p => p.height > page.height - page.overlap);
                if (large) target = Math.max(target,Math.floor(large.top));
            } else {
                target = index > 0 ? history[index - 1] : Math.max(0,current - page.step);
            }
            // 起点不能停在图片中间；向前时只跳过本页已完整显示的图片。
            for (const block of layout.pictures()) {
                const r = block.getBoundingClientRect();
                const top = r.top + current, bottom = r.bottom + current;
                if (target > top + 0.5 && target < bottom - 0.5) {
                    if (direction < 0) target = Math.max(0,Math.floor(top));
                    else if (top >= current - 0.5 && bottom <= current + page.height + 0.5) target = Math.ceil(bottom);
                }
            }
            target = Math.max(0,target);
            layout.prepare(target);
            window.scrollBy({top:target-current,left:0,behavior:'instant'});
            const result = scrollY;
            if (Math.abs(result - current) > 0.5) {
                if (direction < 0 && index > 0) index--;
                else if (direction > 0 && index + 1 < history.length) index++;
                else if (direction > 0) { history = history.slice(0,index+1);history.push(result);index++; }
            }
            expected = result; layoutRevision = layout.revision();
            layout.capture();
            return result;
        }
    };
})();
