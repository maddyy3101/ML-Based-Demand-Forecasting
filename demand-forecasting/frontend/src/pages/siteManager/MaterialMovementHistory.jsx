import { useApiData } from "../../hooks/useApiData";
import { inventoryApi } from "../../api/inventoryApi";
import DataTable from "../../components/shared/DataTable";

export default function MaterialMovementHistory() {
  const movements = useApiData(() => inventoryApi.movements(), []);

  return (
    <DataTable
      loading={movements.loading}
      rows={movements.data || []}
      columns={[
        { key: "timestamp", label: "Timestamp" },
        { key: "materialName", label: "Material" },
        { key: "movementType", label: "Movement Type" },
        { key: "quantity", label: "Quantity" },
        { key: "reason", label: "Reason" },
        { key: "projectId", label: "Project ID" },
        { key: "vendorName", label: "Vendor" },
        { key: "performedBy", label: "Performed By" },
      ]}
    />
  );
}
