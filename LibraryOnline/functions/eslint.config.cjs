"use strict";
module.exports = [{
  files: ["**/*.js", "**/*.cjs"],
  ignores: ["node_modules/**"],
  languageOptions: { ecmaVersion: 2022, sourceType: "commonjs", globals: { __dirname: "readonly", process: "readonly" } },
  rules: {
    "no-unused-vars": "error", "no-undef": "error", "no-redeclare": "error",
    "no-dupe-args": "error", "no-dupe-keys": "error", "no-unreachable": "error",
    "constructor-super": "error", "valid-typeof": "error", "eqeqeq": "error"
  }
}];
