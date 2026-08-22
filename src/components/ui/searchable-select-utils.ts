export type SearchableSelectOption = {
  value: string;
  label: string;
  description?: string;
  keywords?: string;
};

export function normalizeSearchText(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLocaleLowerCase("vi")
    .trim();
}

export function filterSelectOptions(options: readonly SearchableSelectOption[], query: string) {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) return options;
  return options.filter((option) => normalizeSearchText(
    `${option.label} ${option.description ?? ""} ${option.keywords ?? ""}`,
  ).includes(normalizedQuery));
}
