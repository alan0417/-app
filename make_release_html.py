# 從原型網頁產生正式版：拿掉「模擬日期」測試工具，只保留「清除所有資料」
import sys, re
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()
start = s.index("    h += '<h3>測試工具（原型專用）</h3>")
end = s.index("    return h;", start)
s = s[:start] + """    h += '<h3>其他</h3><div class="card">'
      + '<button class="btn danger'+(armed==='reset'?' armed':'')+'" id="sReset" style="width:100%">'+(armed==='reset'?'再按一次確定清除':'清除所有資料，重新開始')+'</button>'
      + '<div class="hint">會刪除這支手機上的所有紀錄，無法復原。</div></div>';
""" + s[end:]
a = "const today = () => S.fakeToday || realToday();"
assert s.count(a) == 1
s = s.replace(a, "const today = () => realToday();")
open(out, 'w', encoding='utf-8').write(s)
print('ok')
