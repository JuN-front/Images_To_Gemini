# Images To Gemini

This is the source code to enable connection with Gemini via API key in **astah-professional** and send a prompt with a maximum of **5 images**.

---

## Prerequisite

Follow the instructions on the official site of [astah professional](https://astah.net/support/plugin-dev-tutorial/) and finish setting up the environment first.  
Make sure the astah plugin SDK and command-line tools (such as `astah-build` and `astah-launch`) are properly configured.

> This prerequisite is required **only if you want to build the plugin from source**.  
> If you only want to use the plugin, refer to  
> **How to download and use without particular environmental setups**.

---

## How to download with environmental setups

This method is for users who want to build or modify the source code.

### Steps to Install

1. **Clone this repository into `astah-plugin-SDK-xx`**

   After completing the environment setup on the command prompt:

   - Open a command prompt.
   - Move to your `astah-plugin-SDK-xx` directory (the **external folder**, not an internal one with the same name).
   - Clone this repository inside that directory.
   - After cloning, you can simply minimize and leave the command prompt as it is.

   Example:

   ```bash
   cd path\to\astah-plugin-SDK-xx
   git clone <this-repo-url>

---

## How to download without environmental setups

This method is for users who directly want to use the plug-in tool in astah-professional without modifying the source code

### Steps to Install

1. Download the .jar file and move it to the following directory:
   ```
   cd path\to\astah-professional\plugins\
   ```
2. Create a file named "images-to-gemini.properties". The content of the file should be as follows:
   ```
   gemini.api.key=your_api_key_here
   ```
   **Note**
   ・The file name must exactly be images-to-gemini.properties
   ・The file must be placed in the same folder as the plugin .jar file
3. Once you confirmed that the .jar file and .properties file are correctly placed, normally open astah-professional and verify if the .jar file is properly loaded. A window should appear within the software screen.
