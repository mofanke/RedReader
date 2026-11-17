"""使用 Chromium 验证 APK 内的网页分页脚本。"""
from pathlib import Path
import os
import unittest
from playwright.sync_api import sync_playwright

SCRIPT = Path(__file__).resolve().parents[1] / 'src/main/assets/eink-reading.js'


class WebPagingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.driver = sync_playwright().start()
        cls.browser = cls.driver.chromium.launch(executable_path=os.environ.get('PAGER_TEST_CHROMIUM'))

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.driver.stop()

    def setUp(self):
        self.page = self.browser.new_page(viewport={'width': 480, 'height': 640})

    def tearDown(self):
        self.page.close()

    def fixture(self, font=20, before=''):
        self.page.goto('about:blank')
        rows = ''.join(f'<p id="p{i}" data-row="{i}">Reading row {i}</p>' for i in range(100))
        self.page.set_content(f'<style>body,p,figure{{margin:0}}p{{font-size:{font}px;line-height:{font*1.5}px}}</style>{before}{rows}')
        self.page.add_script_tag(path=str(SCRIPT))
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')

    def turn(self, direction):
        return self.page.evaluate('d=>HydrogenReadingPager.turn(d,2)', direction)

    def test_every_text_row_is_fully_visible(self):
        for font in [16, 20, 32, 48]:
            self.fixture(font)
            seen = set()
            for _ in range(200):
                seen.update(self.page.evaluate('[...document.querySelectorAll("[data-row]")].filter(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=640}).map(e=>Number(e.dataset.row))'))
                old = self.page.evaluate('scrollY')
                if self.turn(1) == old:
                    break
            self.assertEqual(seen, set(range(100)))

    def test_short_tail_and_reverse_history(self):
        self.fixture()
        starts = [0]
        for _ in range(20):
            current = self.turn(1)
            if current == starts[-1]:
                break
            starts.append(current)
        self.assertEqual(starts, [0, 580, 1160, 1740, 2320, 2900])
        self.assertEqual(self.page.locator('#p99').bounding_box()['y'], 70)
        self.page.add_script_tag(path=str(SCRIPT))
        self.assertEqual(self.turn(-1), 2320)
        self.assertEqual(self.turn(1), 2900)

    def test_long_image_is_whole_and_keeps_colour_and_ratio(self):
        self.fixture(before='<div style="height:500px">Text before image</div><figure><img id="photo" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22500%22 height=%225000%22/%3E"></figure>')
        self.page.wait_for_function('photo.naturalWidth>0')
        self.page.evaluate('HydrogenReadingLayout.init(true,2)')
        self.turn(1)
        rect = self.page.locator('#photo').bounding_box()
        self.assertGreaterEqual(rect['y'], 0)
        self.assertLessEqual(rect['y'] + rect['height'], 640)
        self.assertAlmostEqual(rect['height']/rect['width'], 10, places=2)
        self.assertEqual(self.page.locator('#photo').evaluate('e=>getComputedStyle(e).filter'), 'none')

    def test_initial_header_and_user_return_to_top(self):
        self.fixture()
        self.page.evaluate('document.body.style.paddingTop="100px";HydrogenReadingLayout.init(true,2)')
        self.assertEqual(self.page.evaluate('scrollY'), 0)
        self.turn(1)
        self.page.evaluate('scrollTo({top:0,behavior:"instant"})')
        self.page.evaluate('()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))')
        self.assertEqual(self.page.evaluate('scrollY'), 0)

    def test_actual_visual_viewport_controls_step(self):
        self.fixture()
        session = self.page.context.new_cdp_session(self.page)
        session.send('Emulation.setPageScaleFactor', {'pageScaleFactor': 2})
        height = self.page.evaluate('visualViewport.height')
        self.assertLessEqual(self.turn(1), height - 60)


if __name__ == '__main__':
    unittest.main(verbosity=2)
