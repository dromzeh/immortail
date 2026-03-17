module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "subject-case": [0],
  },
  parserPreset: {
    parserOpts: {
      headerPattern:
        /^(?:\[(?:no ci|skip ci)\] )?(\w*)(?:\((.*)\))?!?: (.*)$/,
      headerCorrespondence: ["type", "scope", "subject"],
    },
  },
};
