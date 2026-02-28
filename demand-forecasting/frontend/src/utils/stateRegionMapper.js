const stateRegionMap = {
  Karnataka: "South",
  "Tamil Nadu": "South",
  Telangana: "South",
  "Uttar Pradesh": "North",
  Rajasthan: "North",
  Bihar: "North",
  "West Bengal": "East",
  Odisha: "East",
  Maharashtra: "West",
  Gujarat: "West",
};

export function mapStateToRegion(state) {
  return stateRegionMap[state] || "";
}

export const states = Object.keys(stateRegionMap);
