// Gives the page a standard webkitSpeechRecognition inside the Android app.
// The work is done by Android's speech recognizer (SpeechBridge.java, exposed as window.NKTSpeech).
(function () {
  if (window.__nktSpeechInstalled || !window.NKTSpeech) return;
  window.__nktSpeechInstalled = true;

  var current = null;

  function list(items) { items.item = function (i) { return this[i]; }; return items; }

  function NKTRecognition() {
    this.lang = 'en-US';
    this.continuous = false;
    this.interimResults = false;
    this.maxAlternatives = 1;
    this.onstart = this.onresult = this.onerror = this.onend = null;
  }
  NKTRecognition.prototype.start = function () {
    if (current) throw new DOMException('recognition has already started', 'InvalidStateError');
    current = this;
    window.NKTSpeech.start(String(this.lang || 'en-US'), !!this.interimResults);
  };
  NKTRecognition.prototype.stop = function () { window.NKTSpeech.stop(); };
  NKTRecognition.prototype.abort = function () { window.NKTSpeech.abort(); };
  NKTRecognition.prototype.addEventListener = function (type, fn) {
    var prev = this['on' + type];
    this['on' + type] = prev ? function (e) { prev(e); fn(e); } : fn;
  };

  window.__nktSpeechEvent = function (type, data) {
    var r = current;
    if (!r) return;
    if (type === 'start') {
      if (r.onstart) r.onstart({ type: 'start' });
    } else if (type === 'result') {
      var result = list([{ transcript: data.text, confidence: 1 }]);
      result.isFinal = !!data.final;
      if (r.onresult) r.onresult({ type: 'result', resultIndex: 0, results: list([result]) });
    } else if (type === 'error') {
      if (r.onerror) r.onerror({ type: 'error', error: data.error });
    } else if (type === 'end') {
      current = null;
      if (r.onend) r.onend({ type: 'end' });
    }
  };

  window.SpeechRecognition = NKTRecognition;
  window.webkitSpeechRecognition = NKTRecognition;
})();
