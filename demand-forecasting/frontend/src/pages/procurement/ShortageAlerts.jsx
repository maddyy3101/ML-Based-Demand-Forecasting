import { useApiData } from "../../hooks/useApiData";
import { planningApi } from "../../api/planningApi";
import DataTable from "../../components/shared/DataTable";
import UrgencyBadge from "../../components/shared/UrgencyBadge";
import StockStatusBadge from "../../components/shared/StockStatusBadge";

export default function ShortageAlerts() {
  const alerts = useApiData(() => planningApi.exceptions(), []);

  return (
    <DataTable
      loading={alerts.loading}
      rows={alerts.data || []}
      columns={[
        { key: "materialType", label: "Material" },
        { key: "region", label: "Region" },
        { key: "stockStatus", label: "Stock Status", render: (v) => <StockStatusBadge status={v} /> },
        { key: "urgency", label: "Urgency", render: (v) => <UrgencyBadge urgency={v} /> },
        { key: "message", label: "Message" },
      ]}
      emptyText="No active shortage or overstock exceptions"
    />
  );
}
