// Monaco Editor setup
require.config({
    paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.36.1/min/vs' },
    'vs/nls': { availableLanguages: { '*': 'en' } }
});

// Load extra features for languages
const loadExtraFeatures = (language) => {
    if (language === 'java') {
        require(['vs/language/java/monaco.contribution'], function() {});
    } else if (language === 'python') {
        require(['vs/language/python/monaco.contribution'], function() {});
    } else if (language === 'c') {
        require(['vs/language/cpp/monaco.contribution'], function() {});
    }
};

let editor;
require(['vs/editor/editor.main'], function() {
    editor = monaco.editor.create(document.getElementById('editor'), {
        value: '// Write your code here\n',
        language: 'java',
        theme: 'vs', // light theme
        minimap: { enabled: false },
        fontSize: 14,
        automaticLayout: true,
        lineHeight: 21,
        padding: { top: 8 },
        scrollBeyondLastLine: false,
        renderLineHighlight: 'line',
        lineNumbers: 'on',
        roundedSelection: false,
        selectOnLineNumbers: true,
        wordWrap: 'on',
        // Autocompletion features
        quickSuggestions: true,
        suggestOnTriggerCharacters: true,
        acceptSuggestionOnEnter: 'on',
        tabCompletion: 'on',
        snippetSuggestions: 'inline',
        suggest: {
            snippetsPreventQuickSuggestions: false,
            showKeywords: true,
            showSnippets: true,
            showClasses: true,
            showFunctions: true,
            showVariables: true
        },
        // IntelliSense features
        parameterHints: { enabled: true },
        hover: { enabled: true },
        lightbulb: { enabled: true }
    });
});

// Language selection handling (keeps your boilerplate templates)
const languageSelect = document.getElementById('languageSelect');
languageSelect.addEventListener('change', () => {
    const newLanguage = languageSelect.value;
    
    if (!newLanguage) {
        // If "SELECT LANGUAGE" is chosen
        editor.setValue('// Select a language to start coding');
        monaco.editor.setModelLanguage(editor.getModel(), 'plaintext');
        return;
    }
    
    monaco.editor.setModelLanguage(editor.getModel(), newLanguage);
    loadExtraFeatures(newLanguage);
    
    // Set language-specific snippets and templates (unchanged behavior)
    if (newLanguage === 'java') {
        editor.setValue(`public class Main {
    public static void main(String[] args) {
        // Your code here
    }
}`);
    } else if (newLanguage === 'python') {
        editor.setValue(`# Your Python code here
`);
    } else if (newLanguage === 'c') {
        editor.setValue(`#include <stdio.h>

int main() {
    // Your code here
    return 0;
}`);
    }
});

// Resizable panels handling
const divider = document.getElementById('divider');
const editorPanel = document.getElementById('editor');
const outputPanel = document.querySelector('.output-panel');

let isDragging = false;

divider.addEventListener('mousedown', (e) => {
    isDragging = true;
    divider.classList.add('dragging');
});

document.addEventListener('mousemove', (e) => {
    if (!isDragging) return;

    const containerRect = document.querySelector('.main-content').getBoundingClientRect();
    const containerWidth = containerRect.width;
    const newWidth = e.clientX - containerRect.left;
    const minWidth = 200; // Minimum width for each panel

    if (newWidth >= minWidth && newWidth <= containerWidth - minWidth) {
        const widthPercentage = (newWidth / containerWidth) * 100;
        editorPanel.style.width = `${widthPercentage}%`;
        outputPanel.style.width = `calc(${100 - widthPercentage}% - 6px)`;
        editor.layout(); // Refresh Monaco editor layout
    }
});

document.addEventListener('mouseup', () => {
    isDragging = false;
    divider.classList.remove('dragging');
});

// Helper: detect if code likely requires stdin (heuristic)
function requiresInput(code) {
    const patterns = [
        /scanf\s*\(/i,
        /\bscanf_s\s*\(/i,
        /\bgets\s*\(/i,
        /\bgetchar\s*\(/i,
        /\bcin\b\s*>>/i,
        /new\s+Scanner\s*\(/i,
        /\bScanner\b\s*\(/i,
        /\binput\s*\(/i,       // python input()
        /\braw_input\s*\(/i   // python2 raw_input
    ];
    return patterns.some(p => p.test(code));
}

// UI elements
const runBtn = document.getElementById('runBtn');
const runWithInputBtn = document.getElementById('runWithInputBtn');
const debugBtn = document.getElementById('debugBtn');
const outputContent = document.getElementById('output-content');
const debugPanel = document.getElementById('debug-panel');
const correctedCodeEl = document.getElementById('corrected-code');
const explanationEl = document.getElementById('explanation');
const replaceBtn = document.getElementById('replaceBtn');

const terminalInputArea = document.getElementById('terminal-input-area');
const terminalInput = document.getElementById('terminal-input');
const sendInputBtn = document.getElementById('sendInputBtn');
const cancelInputBtn = document.getElementById('cancelInputBtn');

// API base
const API_BASE = 'http://localhost:8080/api';

// Run button handling
runBtn.addEventListener('click', () => {
    const code = editor.getValue();
    const language = languageSelect.value;

    if (!language) {
        outputContent.textContent = 'Please select a language.';
        return;
    }

    // If code likely requires input and terminal input area is hidden, show it and wait for user
    if (requiresInput(code) && terminalInputArea.style.display === 'none') {
        terminalInputArea.style.display = 'block';
        terminalInput.value = '';
        terminalInput.focus();
        runWithInputBtn.style.display = 'inline-block';
        runBtn.style.display = 'none';
        return;
    }

    // No stdin required or already provided via terminalInput; proceed to run
    let inputValue = '';
    if (terminalInputArea.style.display !== 'none') {
        inputValue = terminalInput.value || '';
        // hide terminal area after sending
        terminalInputArea.style.display = 'none';
        runWithInputBtn.style.display = 'none';
        runBtn.style.display = 'inline-block';
    }

    // Run code via backend
    runCodeOnServer(code, language, inputValue);
});

// sendInputBtn (in-terminal Run)
sendInputBtn.addEventListener('click', () => {
    const code = editor.getValue();
    const language = languageSelect.value;
    const inputValue = terminalInput.value || '';

    // Hide terminal area and run
    terminalInputArea.style.display = 'none';
    runWithInputBtn.style.display = 'none';
    runBtn.style.display = 'inline-block';
    runCodeOnServer(code, language, inputValue);
});

cancelInputBtn.addEventListener('click', () => {
    terminalInputArea.style.display = 'none';
    runWithInputBtn.style.display = 'none';
    runBtn.style.display = 'inline-block';
});

// Helper to call /api/run
async function runCodeOnServer(code, language, input) {
    outputContent.textContent = 'Running code...';
    try {
        const response = await fetch(`${API_BASE}/run`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, language, input })
        });
        const result = await response.json();
        let display = '';
        if (result.compileOutput) {
            display += '=== Compile Output ===\n' + result.compileOutput + '\n\n';
        }
        if (result.error) {
            display += '=== Runtime Error / Stderr ===\n' + result.error + '\n\n';
        }
        if (result.output) {
            display += '\n' + result.output + '\n\n';
        }
        if (!display) display = 'No output.';
        outputContent.textContent = display;
    } catch (err) {
        outputContent.textContent = 'Failed to connect to backend.';
    }
}

// Debug button handling
debugBtn.addEventListener('click', async () => {
    debugPanel.style.display = 'block';
    const code = editor.getValue();
    const language = languageSelect.value;
    const description = document.getElementById('description').value;

    if (!language) {
        correctedCodeEl.textContent = '';
        explanationEl.textContent = 'Please select a language before requesting AI Debug.';
        return;
    }

    if (!description || description.trim() === '') {
        correctedCodeEl.textContent = '';
        explanationEl.textContent = 'Description is required for AI Debug. Please explain what the program should do.';
        return;
    }

    correctedCodeEl.textContent = 'Loading AI suggestions...';
    explanationEl.textContent = '';
    try {
        const response = await fetch(`${API_BASE}/ai-debug`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, language, description })
        });
        const result = await response.json();
        correctedCodeEl.textContent = result.correctedCode || '// No corrections needed.';
        explanationEl.textContent = result.explanation || 'No explanation.';
        if (result.error) {
            explanationEl.textContent = 'Error: ' + result.error;
        }
    } catch (err) {
        correctedCodeEl.textContent = '';
        explanationEl.textContent = 'Failed to connect to backend.';
    }
});

// Replace button handling
replaceBtn.addEventListener('click', () => {
    const correctedCode = document.getElementById('corrected-code').textContent;
    if (correctedCode && correctedCode.trim() !== '') {
        editor.setValue(correctedCode);
    }
});
