import { useApiData } from "../../hooks/useApiData";
import { adminApi } from "../../api/adminApi";
import DataTable from "../../components/shared/DataTable";
import ProcurementDecisionBadge from "../../components/shared/ProcurementDecisionBadge";

export default function AuditLog() {
  const audit = useApiData(() => adminApi.auditLog({ size: 50 }), []);
  const rows = audit.data?.content || [];

  return (
    <DataTable
      loading={audit.loading}
      rows={rows}
      columns={[
        { key: "requestId", label: "Request ID" },
        { key: "projectId", label: "Project ID" },
        { key: "materialType", label: "Material" },
        { key: "region", label: "Region" },
        { key: "predictedQuantity", label: "Predicted Qty" },
        {
          key: "procurementDecision",
          label: "Decision",
          render: (value) => <ProcurementDecisionBadge decision={value} />,
        },
        { key: "requestedBy", label: "Requested By" },
      ]}
    />
  );
}
