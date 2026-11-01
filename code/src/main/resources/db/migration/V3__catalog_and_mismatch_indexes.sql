CREATE INDEX products_catalog_order ON products(business_id,name,id);
CREATE INDEX counts_mismatch_history ON count_entries(business_id,created_at DESC,id DESC)
WHERE physical_count <> recorded_count;
