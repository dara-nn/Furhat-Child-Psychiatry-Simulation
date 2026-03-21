import urllib.request
import urllib.parse
import json
import time

url = 'http://localhost:1932/furhat/send'
headers = {'Content-Type': 'application/json'}

def send_event(name, data=None):
    if data is None: data = {}
    payload = json.dumps({'event_name': name, 'event_data': data}).encode('utf-8')
    req = urllib.request.Request(url, data=payload, headers=headers)
    try:
        response = urllib.request.urlopen(req)
        print(f"Sent {name}: {response.read()}")
    except Exception as e:
        print(f"Error sending {name}: {e}")

time.sleep(2)
send_event("furhatos.event.senses.SenseNetworkSpeechGiven", {"text": "hello"})
