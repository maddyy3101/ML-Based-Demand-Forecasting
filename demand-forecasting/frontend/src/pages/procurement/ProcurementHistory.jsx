import { useApiData } from "../../hooks/useApiData";
import { forecastApi } from "../../api/forecastApi";
import DataTable from "../../components/shared/DataTable";
import ProcurementDecisionBadge from "../../components/shared/ProcurementDecisionBadge";

export default function ProcurementHistory() {
  const history = useApiData(() => forecastApi.history({ size: 100 }), []);
  const rows = history.data?.content || [];

  return (
    <DataTable
      loading={history.loading}
      rows={rows}
      columns={[
        { key: "requestId", label: "Request ID" },
        { key: "projectId", label: "Project ID" },
        { key: "materialType", label: "Material" },
        { key: "region", label: "Region" },
        { key: "predictedQuantity", label: "Predicted Qty" },
        { key: "actualQuantity", label: "Actual Qty" },
        { key: "procurementDecision", label: "Decision", render: (v) => <ProcurementDecisionBadge decision={v} /> },
      ]}
    />
  );
}
