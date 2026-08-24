/**
 * Thêm bốn khoá cho nhóm `draw` vào cả sáu file ngôn ngữ.
 *
 * VÌ SAO DÙNG SCRIPT: chữ tiếng Việt và tiếng Trung/Nhật/Hàn đã bị hỏng nhiều lần khi
 * ghi trực tiếp qua công cụ sửa file. Đọc JSON, chèn khoá, rồi ghi lại bằng
 * `JSON.stringify` đảm bảo mã hoá UTF-8 nhất quán và không phá thứ tự các khoá khác.
 *
 * Chèn NGAY SAU `load_failed` để bốn khoá mới nằm cạnh các khoá cùng nhóm, thay vì bị
 * đẩy xuống sau bảng `tier_*`.
 *
 * Chạy lại nhiều lần được: khoá đã có thì bỏ qua, không ghi đè.
 */

import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const LOCALES_DIR = join(import.meta.dirname, "..", "src", "locales");

/** Bốn khoá mới, dịch theo đúng nhãn của trang gốc ở từng ngôn ngữ. */
const NEW_KEYS = {
  vi: {
    history_title: "Quay số",
    more_result: "Kết quả khác",
    match_column: "Ván đấu",
    result_column: "Kết quả",
  },
  en: {
    history_title: "Draw",
    more_result: "More result",
    match_column: "Match",
    result_column: "Result",
  },
  zh: {
    history_title: "开奖",
    more_result: "更多结果",
    match_column: "场次",
    result_column: "结果",
  },
  ms: {
    history_title: "Cabutan",
    more_result: "Keputusan lain",
    match_column: "Perlawanan",
    result_column: "Keputusan",
  },
  ja: {
    history_title: "抽選",
    more_result: "他の結果",
    match_column: "試合",
    result_column: "結果",
  },
  ko: {
    history_title: "추첨",
    more_result: "다른 결과",
    match_column: "경기",
    result_column: "결과",
  },
};

/** Khoá đứng trước vị trí chèn. Bốn khoá mới sẽ nằm ngay sau nó. */
const INSERT_AFTER = "load_failed";

let changed = 0;
let skipped = 0;

for (const [locale, additions] of Object.entries(NEW_KEYS)) {
  const path = join(LOCALES_DIR, `${locale}.json`);
  const data = JSON.parse(readFileSync(path, "utf8"));

  if (!data.draw) {
    throw new Error(`${locale}.json: không có nhóm "draw"`);
  }

  // Dựng lại nhóm `draw` theo thứ tự mong muốn thay vì gán thêm vào cuối: object trong
  // JS giữ thứ tự chèn, nên gán thêm sẽ đẩy khoá mới xuống dưới bảng tier_*.
  const rebuilt = {};
  for (const [key, value] of Object.entries(data.draw)) {
    rebuilt[key] = value;

    if (key === INSERT_AFTER) {
      for (const [newKey, newValue] of Object.entries(additions)) {
        if (newKey in data.draw) {
          skipped += 1;
          continue;
        }
        rebuilt[newKey] = newValue;
        changed += 1;
      }
    }
  }

  data.draw = rebuilt;

  // 2 dấu cách + newline cuối file, khớp định dạng các file locale hiện có.
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, "utf8");
  console.log(`${locale}.json: đã ghi`);
}

console.log(`\nThêm ${changed} khoá, bỏ qua ${skipped} khoá đã tồn tại.`);

// ---------------------------------------------------------------------------
// Đọc lại TỪ ĐĨA để đối chiếu — bước này không được bỏ.
//
// Ghi xong không có nghĩa là ghi đúng: lỗi mã hoá chỉ lộ ra khi đọc lại và so từng
// ký tự với giá trị mong đợi.
// ---------------------------------------------------------------------------
console.log("\n=== Đối chiếu sau khi ghi ===");

let failed = 0;

for (const [locale, additions] of Object.entries(NEW_KEYS)) {
  const path = join(LOCALES_DIR, `${locale}.json`);
  const reread = JSON.parse(readFileSync(path, "utf8"));

  for (const [key, expected] of Object.entries(additions)) {
    const actual = reread.draw?.[key];
    if (actual !== expected) {
      console.error(
        `  LỖI ${locale}.draw.${key}: mong "${expected}", nhận "${actual}"`
      );
      failed += 1;
    }
  }
}

if (failed > 0) {
  console.error(`\n${failed} khoá KHÔNG khớp.`);
  process.exit(1);
}

console.log("Cả 24 khoá khớp chính xác.");

// ---------------------------------------------------------------------------
// Sáu ngôn ngữ phải có đúng cùng tập khoá trong nhóm `draw`.
//
// Thiếu khoá ở một ngôn ngữ thì người dùng ngôn ngữ đó thấy chuỗi khoá thô trên màn
// hình, mà lỗi này chỉ hiện ra khi có người thực sự đổi sang ngôn ngữ đó.
// ---------------------------------------------------------------------------
const keySets = Object.keys(NEW_KEYS).map((locale) => {
  const data = JSON.parse(readFileSync(join(LOCALES_DIR, `${locale}.json`), "utf8"));
  return { locale, keys: Object.keys(data.draw).sort() };
});

const reference = keySets[0];
let mismatch = false;

for (const { locale, keys } of keySets.slice(1)) {
  const missing = reference.keys.filter((k) => !keys.includes(k));
  const extra = keys.filter((k) => !reference.keys.includes(k));

  if (missing.length > 0 || extra.length > 0) {
    console.error(`  ${locale}: thiếu [${missing}], thừa [${extra}]`);
    mismatch = true;
  }
}

if (mismatch) {
  console.error("\nTập khoá KHÔNG đồng nhất giữa các ngôn ngữ.");
  process.exit(1);
}

console.log(
  `Sáu ngôn ngữ có cùng ${reference.keys.length} khoá trong nhóm draw.`
);
