/**
 * Chuyển ảnh JPEG trong public/images sang WebP và thu về đúng kích thước cần dùng.
 *
 * Vì sao cần: các ảnh gốc đều 1376x768 và nặng 700 KB – 1.1 MB, trong khi khung
 * nội dung chỉ rộng 640px. Ô lưới 2 cột rộng khoảng 310px, nên ảnh 1376px là dư
 * hơn gấp đôi kể cả sau khi nhân 2 cho màn hình retina.
 *
 * Chạy: node scripts/optimize-images.mjs
 */
import sharp from "sharp";
import fs from "node:fs";
import path from "node:path";

/**
 * Bề rộng đích, tính theo cách ảnh được dùng thật:
 *  - banner: rộng hết khung 640px -> 1280px cho màn hình retina 2x
 *  - thumb/table: ô lưới 2 cột ~310px -> 640px cho 2x
 */
const JOBS = [
  ["public/images/banner_gateway.jpg", 1280],
  ["public/images/banner_promo.jpg", 1280],
  ["public/images/thumb_dealer.jpg", 640],
  ["public/images/thumb_mbappe.jpg", 640],
  ["public/images/thumb_neymar.jpg", 640],
  ["public/images/thumb_ronaldo.jpg", 640],
  ["public/images/tables/baccarat.jpg", 640],
  ["public/images/tables/lottery.jpg", 640],
  ["public/images/tables/roulette.jpg", 640],
];

let before = 0;
let after = 0;

for (const [src, width] of JOBS) {
  if (!fs.existsSync(src)) {
    console.log(`BO QUA (khong ton tai): ${src}`);
    continue;
  }
  const dest = path.join(
    path.dirname(src),
    `${path.basename(src, path.extname(src))}.webp`
  );
  const sizeBefore = fs.statSync(src).size;
  // withoutEnlargement: không phóng to ảnh nhỏ hơn mức đích, phóng to chỉ làm file
  // nặng thêm mà không thêm chi tiết nào.
  const info = await sharp(src)
    .resize({ width, withoutEnlargement: true })
    .webp({ quality: 80, effort: 6 })
    .toFile(dest);

  before += sizeBefore;
  after += info.size;
  console.log(
    `${path.basename(src).padEnd(22)} ${(sizeBefore / 1024)
      .toFixed(0)
      .padStart(5)} KB -> ${(info.size / 1024).toFixed(0).padStart(4)} KB  (${
      info.width
    }x${info.height})`
  );
}

console.log("---");
console.log(
  `TONG: ${(before / 1024 / 1024).toFixed(2)} MB -> ${(after / 1024).toFixed(
    0
  )} KB  (giam ${(100 - (after / before) * 100).toFixed(1)}%)`
);
