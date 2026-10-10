#!/usr/bin/env python3
"""Create a LOCAL connection/provider, publish a service, and invoke/replay it.
Run against your own local instance. Uses HUB_TOKEN without printing it.
Creates synthetic persistent records intentionally; it does not delete history.
"""
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('HUB_URL','http://127.0.0.1:8080').rstrip('/')
TOKEN = os.environ.get('HUB_TOKEN')
if not TOKEN:
    raise SystemExit('Set HUB_TOKEN to your local administrator key before running this example.')
def call(path, data, key=None):
    headers={'Authorization':'Bearer '+TOKEN,'Content-Type':'application/json'}
    if key: headers['Idempotency-Key']=key
    request=urllib.request.Request(BASE+path,data=json.dumps(data).encode(),headers=headers,method='POST')
    try:
        with urllib.request.urlopen(request,timeout=20) as response:return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f'HTTP {error.code}: {error.read().decode()}') from error
suffix=uuid.uuid4().hex[:10]
connection=call('/api/connections',{'name':'Example '+suffix,'type':'LOCAL','config':{}})
provider=call('/api/providers',{'name':'Synthetic create '+suffix,'slug':'synthetic-create-'+suffix,'connectionId':connection['id'],'operation':'CREATE','parameters':[{'name':'record','type':'JSON','source':'INPUT','required':True}],'config':{'collection':'example_records','body':'${record}'},'fieldsRoot':'/data','maxAttempts':1})
publication=call('/api/providers/'+provider['id']+'/publish',{'version':provider['version']})
key='example-'+uuid.uuid4().hex
request={'input':{'record':{'name':'Synthetic integration example','source':'publish-local.py'}}}
created=call(publication['path'],request,key)
replayed=call(publication['path'],request,key)
assert created['data']==replayed['data']
assert replayed['replayed'] is True
print(json.dumps({'url':BASE+publication['path'],'result':created,'replayVerified':True},indent=2))
