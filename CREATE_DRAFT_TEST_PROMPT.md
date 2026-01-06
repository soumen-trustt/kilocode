# Create Draft Tool - Test Prompt

Use this prompt in debug mode to test all capabilities of the create_draft tool:

---

Please test the create_draft tool by performing the following steps:

1. **Create a draft document**: Use create_draft to create a planning document with the title "test-plan" and initial content that includes:

    - A heading "# Test Plan"
    - A section "## Overview" with some text
    - A section "## Steps" with a few bullet points
    - A section "## Notes" that is initially empty

2. **Read the draft back**: Use read_file with the draft:// path that was returned from step 1 to verify the content was saved correctly.

3. **Update the draft**: Use write_to_file with the draft:// path to:

    - Add content to the "## Notes" section
    - Modify one of the steps in "## Steps"
    - Add a new section "## Status" with current progress

4. **Verify updates**: Read the draft again using read_file to confirm all your changes were saved.

5. **Create a second draft**: Create another draft document with title "second-test-plan" and different content to verify multiple drafts can coexist.

6. **Read both drafts**: Read both draft documents to verify they are separate and independent.

7. **Test error handling**: Try to read a non-existent draft:// path (e.g., "draft://nonexistent.md") to verify proper error handling.

8. **Verify editor integration**: Confirm that:
    - Draft documents appear as editor tabs
    - You can see the draft:// URI in the tab
    - The documents are editable in the editor

Please report:

- Whether all operations succeeded
- Any errors encountered
- Whether the draft:// paths work correctly with read_file and write_to_file
- Whether multiple drafts can exist simultaneously
- Whether error handling works for missing drafts




