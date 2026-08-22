import assert from "node:assert/strict";
import test from "node:test";
import { filterSelectOptions, normalizeSearchText } from "../../src/components/ui/searchable-select-utils.ts";

const options = [
  { value: "1", label: "Giáo viên Nguyễn Thị Ánh" },
  { value: "2", label: "Lớp 10A1", description: "Năm học 2026-2027" },
  { value: "3", label: "Văn phòng" },
];

test("chuẩn hóa tìm kiếm không phân biệt dấu và chữ hoa", () => {
  assert.equal(normalizeSearchText(" Đặng Ánh "), "dang anh");
});

test("lọc theo nhãn và mô tả bằng từ khóa không dấu", () => {
  assert.deepEqual(filterSelectOptions(options, "nguyen thi anh").map((item) => item.value), ["1"]);
  assert.deepEqual(filterSelectOptions(options, "2026").map((item) => item.value), ["2"]);
  assert.deepEqual(filterSelectOptions(options, "van phong").map((item) => item.value), ["3"]);
});
