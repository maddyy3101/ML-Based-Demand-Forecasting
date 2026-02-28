import { useApiData } from "../../hooks/useApiData";
import { inventoryApi } from "../../api/inventoryApi";
import DataTable from "../../components/shared/DataTable";
import StockStatusBadge from "../../components/shared/StockStatusBadge";

export default function WarehouseInventory() {
  const items = useApiData(() => inventoryApi.items(), []);

  return (
    <DataTable
      loading={items.loading}
      rows={items.data || []}
      columns={[
        { key: "sku", label: "SKU" },
        { key: "materialName", label: "Material" },
        { key: "region", label: "Region" },
        { key: "towerType", label: "Tower Type" },
        { key: "currentStock", label: "Current Stock" },
        { key: "unitLabel", label: "Unit" },
        { key: "reorderThreshold", label: "Reorder Threshold" },
        { key: "stockStatus", label: "Status", render: (v) => <StockStatusBadge status={v} /> },
      ]}
    />
  );
}
