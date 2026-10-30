# 發薪日預算

以「發薪日」為週期的記帳 App：設定每月收入與預期支出，記帳時扣在對應項目，隨時看到到下次發薪日前每天還能花多少。

- 資料只存在手機裡，不需要網路、不用登入
- 發薪日自動開新的一期，剩下的錢自動轉入「上月結餘」

## 下載

到右側的 **Releases** 下載最新的 `payday-budget-*.apk`，在 Android 手機上開啟安裝。
第一次安裝時，手機會問是否允許「安裝不明來源的應用程式」，請選允許。

## 專案結構

- `app/src/main/assets/index.html`：App 的畫面與功能（整個 App 就是這個網頁）
- `app/src/main/java/.../MainActivity.java`：把網頁包成 Android App 的外殼
- `.github/workflows/build-apk.yml`：GitHub 自動打包 apk 的設定
