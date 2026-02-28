import { useState } from "react";
import { forecastApi } from "../../api/forecastApi";
import DataTable from "../../components/shared/DataTable";
import ProcurementDecisionBadge from "../../components/shared/ProcurementDecisionBadge";

const templateHeader =
  "Project_ID,Project_Phase,State,Region,Terrain_Type,Tower_Type,Substation_Type,Transmission_Length_KM,Budget_Crore,Material_Type,Lead_Time_Days,Tax_Percentage,Transportation_Cost,Historical_Consumption,Month,Year";

export default function MultiProjectForecast() {
  const [rows, setRows] = useState([]);
  const [results, setResults] = useState([]);

  const downloadTemplate = () => {
    const blob = new Blob([templateHeader], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "multi_project_template.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  const onFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    const lines = text.split(/\r?\n/).filter(Boolean);
    const headers = lines[0].split(",");
    const data = lines.slice(1).map((line) => {
      const cells = line.split(",");
      const o = {};
      headers.forEach((h, i) => {
        o[h] = cells[i];
      });
      return o;
    });
    setRows(data);
  };

  const run = async () => {
    const payload = {
      requests: rows.slice(0, 50).map((row) => ({
        projectId: row.Project_ID,
        projectPhase: row.Project_Phase,
        state: row.State,
        region: row.Region,
        terrainType: row.Terrain_Type,
        towerType: row.Tower_Type,
        substationType: row.Substation_Type,
        transmissionLengthKm: Number(row.Transmission_Length_KM),
        budgetCrore: Number(row.Budget_Crore),
        materialType: row.Material_Type,
        leadTimeDays: Number(row.Lead_Time_Days),
        taxPercentage: Number(row.Tax_Percentage),
        transportationCost: Number(row.Transportation_Cost),
        historicalConsumption: Number(row.Historical_Consumption),
        forecastMonth: Number(row.Month),
        forecastYear: Number(row.Year),
      })),
    };
    const data = await forecastApi.batch(payload);
    setResults(data);
  };

  return (
    <div className="space-y-4">
      <div className="pg-card p-4 flex flex-wrap gap-3 items-center">
        <button className="pg-btn" onClick={downloadTemplate}>Download CSV Template</button>
        <input type="file" accept=".csv" onChange={onFile} />
        <button className="pg-btn pg-btn-primary" onClick={run}>Run Multi-Project Forecast</button>
      </div>

      <DataTable
        rows={results}
        columns={[
          { key: "projectId", label: "Project ID" },
          { key: "materialType", label: "Material" },
          { key: "projectPhase", label: "Phase" },
          { key: "requestId", label: "Region" },
          { key: "predictedQuantity", label: "Predicted Qty" },
          { key: "unitLabel", label: "Unit" },
          { key: "procurementDecision", label: "Decision", render: (v) => <ProcurementDecisionBadge decision={v} /> },
          { key: "forecastId", label: "Action", render: () => <button className="pg-btn">View</button> },
        ]}
        emptyText="Upload CSV and run forecast"
      />
    </div>
  );
}
