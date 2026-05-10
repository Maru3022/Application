const appJson = require("./app.json");

module.exports = ({ config }) => {
  const merged = { ...appJson.expo, ...config };
  const projectId = process.env.EAS_PROJECT_ID;

  merged.extra = merged.extra || {};
  merged.extra.eas = merged.extra.eas || {};

  if (projectId) {
    merged.extra.eas.projectId = projectId;
  } else if (process.env.APP_ENV === "production") {
    throw new Error("EAS_PROJECT_ID is required for production builds");
  }

  return { expo: merged };
};
